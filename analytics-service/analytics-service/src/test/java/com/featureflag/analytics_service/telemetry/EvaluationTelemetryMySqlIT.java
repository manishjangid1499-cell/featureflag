package com.featureflag.analytics_service.telemetry;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.entity.ProcessedEvent;
import com.featureflag.analytics_service.event.FlagEvent;
import com.featureflag.analytics_service.kafka.EvaluationTelemetryConsumer;
import com.featureflag.analytics_service.repository.AnalyticsEventRepository;
import com.featureflag.analytics_service.repository.ProcessedEventRepository;
import com.featureflag.analytics_service.service.AnalyticsService;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        classes = EvaluationTelemetryMySqlIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.main.banner-mode=off"
)
@ActiveProfiles("mysql-it")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Testcontainers
class EvaluationTelemetryMySqlIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11");

    private final EvaluationTelemetryConsumer consumer;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvaluationTelemetryMySqlIT(
            EvaluationTelemetryConsumer consumer,
            JdbcTemplate jdbcTemplate
    ) {
        this.consumer = consumer;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void clearBusinessData() {
        jdbcTemplate.update("DELETE FROM processed_kafka_events");
        jdbcTemplate.update("DELETE FROM analytics_events");
    }

    @Test
    void evaluationTelemetryAggregatesOutcomesAndDeduplicatesEventIds() {
        FlagEvent enabledOne = event(
                "evaluation-enabled-1",
                FlagEvent.EVALUATION_ENABLED
        );
        consumer.consume(enabledOne);
        consumer.consume(event(
                "evaluation-enabled-2",
                FlagEvent.EVALUATION_ENABLED
        ));
        consumer.consume(event(
                "evaluation-disabled-1",
                FlagEvent.EVALUATION_DISABLED
        ));

        consumer.consume(enabledOne);

        assertEquals(
                2L,
                aggregateCount(FlagEvent.EVALUATION_ENABLED)
        );
        assertEquals(
                1L,
                aggregateCount(FlagEvent.EVALUATION_DISABLED)
        );
        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM analytics_events",
                        Integer.class
                )
        );
        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM processed_kafka_events",
                        Integer.class
                )
        );
        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM processed_kafka_events
                        WHERE topic = 'feature-flag-evaluations'
                        """,
                        Integer.class
                )
        );
    }

    private Long aggregateCount(String eventType) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count
                FROM analytics_events
                WHERE flag_key = 'checkout'
                  AND environment = 'DEV'
                  AND event_type = ?
                """,
                Long.class,
                eventType
        );
    }

    private FlagEvent event(
            String eventId,
            String eventType
    ) {
        return new FlagEvent(
                eventId,
                eventType,
                "checkout",
                "DEV",
                "2026-08-27T12:00:00Z"
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = KafkaAutoConfiguration.class)
    @EntityScan(basePackageClasses = {
            AnalyticsEvent.class,
            ProcessedEvent.class
    })
    @EnableJpaRepositories(basePackageClasses = {
            AnalyticsEventRepository.class,
            ProcessedEventRepository.class
    })
    @Import({
            AnalyticsService.class,
            EvaluationTelemetryConsumer.class
    })
    static class TestApplication {
    }
}
