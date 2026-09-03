package com.featureflag.flag_service.outbox;

import com.featureflag.flag_service.service.OutboxRetentionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("mysql-it")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@Import({
        OutboxRetentionService.class,
        OutboxRetentionMySqlIT.FixedClockConfiguration.class
})
@TestPropertySource(properties = {
        "outbox.retention.published-age=PT168H",
        "outbox.retention.batch-size=2"
})
class OutboxRetentionMySqlIT {

    private static final Instant NOW =
            Instant.parse("2026-09-01T12:00:00Z");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxRetentionService retentionService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        insert("00000000-0000-0000-0000-000000000001",
                "PUBLISHED", "2026-08-01 00:00:00.000000");
        insert("00000000-0000-0000-0000-000000000002",
                "PUBLISHED", "2026-08-02 00:00:00.000000");
        insert("00000000-0000-0000-0000-000000000003",
                "PUBLISHED", "2026-08-03 00:00:00.000000");
        insert("00000000-0000-0000-0000-000000000004",
                "PUBLISHED", "2026-08-31 00:00:00.000000");
        insert("00000000-0000-0000-0000-000000000005",
                "PENDING", null);
        insert("00000000-0000-0000-0000-000000000006",
                "DEAD", null);
    }

    @Test
    void deletesOnlyExpiredPublishedEventsAndHonorsBatchLimit() {
        assertThat(retentionService.deleteExpiredPublishedEvents())
                .isEqualTo(2);

        assertThat(ids()).containsExactly(
                "00000000-0000-0000-0000-000000000003",
                "00000000-0000-0000-0000-000000000004",
                "00000000-0000-0000-0000-000000000005",
                "00000000-0000-0000-0000-000000000006"
        );
        assertThat(countByStatus("PENDING")).isOne();
        assertThat(countByStatus("DEAD")).isOne();

        assertThat(retentionService.deleteExpiredPublishedEvents())
                .isOne();
        assertThat(retentionService.deleteExpiredPublishedEvents())
                .isZero();
        assertThat(ids()).containsExactly(
                "00000000-0000-0000-0000-000000000004",
                "00000000-0000-0000-0000-000000000005",
                "00000000-0000-0000-0000-000000000006"
        );
    }

    @Test
    void retentionIndexSupportsTheBoundedCleanupAccessPath() {
        List<Map<String, Object>> explain = jdbcTemplate.queryForList(
                """
                EXPLAIN SELECT id
                FROM outbox_events
                WHERE status = 'PUBLISHED'
                  AND published_at < '2026-08-25 12:00:00.000000'
                ORDER BY published_at, id
                LIMIT 2
                """
        );

        assertThat(explain).hasSize(1);
        assertThat(explain.getFirst().get("key"))
                .isEqualTo("idx_outbox_status_published_at_id");
        assertThat(String.valueOf(explain.getFirst().get("Extra")))
                .doesNotContain("filesort");
    }

    private void insert(
            String id,
            String status,
            String publishedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    attempts,
                    created_at,
                    next_attempt_at,
                    published_at,
                    status,
                    id,
                    event_type,
                    topic,
                    message_key,
                    payload
                ) VALUES (
                    0,
                    '2026-08-01 00:00:00.000000',
                    '2026-08-01 00:00:00.000000',
                    ?,
                    ?,
                    ?,
                    'FLAG_UPDATED',
                    'feature-flag-events',
                    'checkout',
                    '{}'
                )
                """,
                publishedAt,
                status,
                id
        );
    }

    private List<String> ids() {
        return jdbcTemplate.queryForList(
                "SELECT id FROM outbox_events ORDER BY id",
                String.class
        );
    }

    private long countByStatus(String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status = ?",
                Long.class,
                status
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
