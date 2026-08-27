package com.featureflag.analytics_service.migration;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("mysql-it")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class AnalyticsFreshMigrationMySqlIT {

    private static final String EVENT_ID =
            "fresh-analytics-event-001";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void freshSchemaMigratesAndValidates() {
        assertNotNull(entityManagerFactory);
        assertSuccessfulMigrations();
        AnalyticsMigrationSchemaAssertions.assertMigratedSchema(
                jdbcTemplate
        );
        assertNoBootstrapRows();
        assertDuplicateAggregateKeysRejected();
        assertAggregateLookupUsesUniqueIndex();
        assertDuplicateProcessedEventRejected();
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    private void assertSuccessfulMigrations() {
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version = '1'
                          AND type = 'SQL'
                          AND success = 1
                        """,
                        Integer.class
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version = '2'
                          AND type = 'SQL'
                          AND success = 1
                        """,
                        Integer.class
                )
        );
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE success = 0
                        """,
                        Integer.class
                )
        );
    }

    private void assertNoBootstrapRows() {
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM analytics_events",
                        Integer.class
                )
        );
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM processed_kafka_events",
                        Integer.class
                )
        );
    }

    private void assertDuplicateAggregateKeysRejected() {
        insertAggregate(5L);
        assertThrows(
                DataAccessException.class,
                () -> insertAggregate(4L)
        );

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM analytics_events
                        WHERE flag_key = 'fresh-checkout'
                          AND environment = 'DEV'
                          AND event_type = 'FLAG_EVALUATED'
                        """,
                        Integer.class
                )
        );
    }

    private void assertAggregateLookupUsesUniqueIndex() {
        Map<String, Object> explain = jdbcTemplate.queryForMap(
                """
                EXPLAIN
                SELECT id, count
                FROM analytics_events
                WHERE flag_key = 'fresh-checkout'
                  AND environment = 'DEV'
                  AND event_type = 'FLAG_EVALUATED'
                """
        );

        assertEquals(
                "uk_analytics_events_dimensions",
                explain.get("key")
        );
    }

    private void insertAggregate(long count) {
        jdbcTemplate.update(
                """
                INSERT INTO analytics_events (
                    count,
                    environment,
                    event_type,
                    flag_key
                ) VALUES (
                    ?,
                    'DEV',
                    'FLAG_EVALUATED',
                    'fresh-checkout'
                )
                """,
                count
        );
    }

    private void assertDuplicateProcessedEventRejected() {
        insertProcessedEvent(EVENT_ID);
        assertThrows(
                DataAccessException.class,
                () -> insertProcessedEvent(EVENT_ID)
        );
    }

    private void insertProcessedEvent(String eventId) {
        jdbcTemplate.update(
                """
                INSERT INTO processed_kafka_events (
                    processed_at,
                    event_id,
                    topic
                ) VALUES (
                    '2026-08-26 12:00:00.000000',
                    ?,
                    'feature-flag-events'
                )
                """,
                eventId
        );
    }
}
