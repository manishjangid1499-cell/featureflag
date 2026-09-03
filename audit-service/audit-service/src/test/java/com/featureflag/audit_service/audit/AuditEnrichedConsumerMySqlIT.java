package com.featureflag.audit_service.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.audit_service.entity.AuditLog;
import com.featureflag.audit_service.entity.ProcessedEvent;
import com.featureflag.audit_service.event.FlagAuditSnapshot;
import com.featureflag.audit_service.event.FlagEvent;
import com.featureflag.audit_service.kafka.AuditEventConsumer;
import com.featureflag.audit_service.repository.AuditLogRepository;
import com.featureflag.audit_service.repository.ProcessedEventRepository;
import com.featureflag.audit_service.observability.AuditMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        classes = AuditEnrichedConsumerMySqlIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.main.banner-mode=off"
)
@ActiveProfiles("mysql-it")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Testcontainers
class AuditEnrichedConsumerMySqlIT {

    @MockitoBean
    private AuditMetrics auditMetrics;

    private static final LocalDateTime OCCURRED_AT =
            LocalDateTime.parse("2026-08-27T12:00:00.123456");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11");

    @Autowired
    private AuditEventConsumer consumer;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearBusinessData() {
        jdbcTemplate.update("DELETE FROM processed_kafka_events");
        jdbcTemplate.update("DELETE FROM audit_logs");
    }

    @Test
    void enrichedUpdatePersistsExactlyOnceWithMarker()
            throws Exception {
        FlagEvent event = enrichedUpdate();

        consumer.consume(event);
        consumer.consume(event);

        assertEquals(1, auditLogRepository.count());
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM processed_kafka_events",
                        Integer.class
                )
        );

        AuditLog auditLog = auditLogRepository.findAll().getFirst();
        assertThat(auditLog.getEventId()).isEqualTo("audit-update-1");
        assertThat(auditLog.getEventType()).isEqualTo("FLAG_UPDATED");
        assertThat(auditLog.getFlagKey()).isEqualTo("checkout");
        assertThat(auditLog.getEnvironment()).isEqualTo("DEV");
        assertThat(auditLog.getSourceService()).isEqualTo("flag-service");
        assertThat(auditLog.getActor()).isEqualTo("actor-123");
        assertThat(auditLog.getOccurredAt())
                .isEqualTo(OCCURRED_AT.toInstant(ZoneOffset.UTC));
        assertThat(auditLog.getTimestamp())
                .isEqualTo("2026-08-27T12:00:00.123456");

        JsonNode before = objectMapper.readTree(
                auditLog.getBeforeState()
        );
        JsonNode after = objectMapper.readTree(
                auditLog.getAfterState()
        );
        assertThat(before.get("description").asText())
                .isEqualTo("old description");
        assertThat(before.get("enabled").asBoolean()).isFalse();
        assertThat(after.get("description").asText())
                .isEqualTo("new description");
        assertThat(after.get("enabled").asBoolean()).isTrue();

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM processed_kafka_events
                        WHERE event_id = 'audit-update-1'
                          AND topic = 'feature-flag-events'
                        """,
                        Integer.class
                )
        );
    }

    @Test
    void flagHistoryQueryUsesTemporalIndexAndOrdering() {
        for (int index = 0; index < 200; index++) {
            insertAuditRow(
                    "other-" + index,
                    "other-flag-" + index,
                    LocalDateTime.of(2026, 8, 1, 0, 0)
                            .plusMinutes(index)
            );
        }
        insertAuditRow(
                "checkout-1",
                "checkout",
                LocalDateTime.parse("2026-08-27T10:00:00")
        );
        insertAuditRow(
                "checkout-2",
                "checkout",
                LocalDateTime.parse("2026-08-27T11:00:00")
        );

        List<AuditLog> history = auditLogRepository
                .findByFlagKeyOrderByOccurredAtDescIdDesc(
                        "checkout"
                );
        assertThat(history)
                .extracting(AuditLog::getEventId)
                .containsExactly("checkout-2", "checkout-1");

        List<String> selectedKeys = jdbcTemplate.query(
                """
                EXPLAIN
                SELECT *
                FROM audit_logs
                WHERE flag_key = 'checkout'
                ORDER BY occurred_at DESC, id DESC
                LIMIT 20
                """,
                (resultSet, rowNumber) -> resultSet.getString("key")
        );
        assertThat(selectedKeys)
                .contains("idx_audit_logs_flag_occurred_at");
    }

    private void insertAuditRow(
            String eventId,
            String flagKey,
            LocalDateTime occurredAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    event_id,
                    event_type,
                    flag_key,
                    environment,
                    timestamp,
                    source_service,
                    occurred_at
                ) VALUES (?, 'FLAG_UPDATED', ?, 'DEV', ?,
                          'flag-service', ?)
                """,
                eventId,
                flagKey,
                occurredAt.toString(),
                Timestamp.valueOf(occurredAt)
        );
    }

    private FlagEvent enrichedUpdate() {
        return new FlagEvent(
                "audit-update-1",
                "FLAG_UPDATED",
                "checkout",
                "DEV",
                "2026-08-27T12:00:00.123456",
                "flag-service",
                "actor-123",
                snapshot(false, "old description"),
                snapshot(true, "new description"),
                OCCURRED_AT
        );
    }

    private FlagAuditSnapshot snapshot(
            boolean enabled,
            String description
    ) {
        return new FlagAuditSnapshot(
                10L,
                "checkout",
                "Checkout",
                description,
                "DEV",
                enabled,
                50,
                null,
                null,
                List.of("user-1")
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = KafkaAutoConfiguration.class)
    @EntityScan(basePackageClasses = {
            AuditLog.class,
            ProcessedEvent.class
    })
    @EnableJpaRepositories(basePackageClasses = {
            AuditLogRepository.class,
            ProcessedEventRepository.class
    })
    @Import(AuditEventConsumer.class)
    static class TestApplication {
    }
}
