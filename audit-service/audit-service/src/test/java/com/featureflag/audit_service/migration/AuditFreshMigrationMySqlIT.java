package com.featureflag.audit_service.migration;

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
class AuditFreshMigrationMySqlIT {

    private static final String EVENT_ID = "fresh-audit-event-001";

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
        AuditMigrationSchemaAssertions.assertMigratedSchema(jdbcTemplate);
        assertNoBootstrapRows();
        assertDuplicateProcessedEventRejected();
        assertDuplicateAuditEventRejected();
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    private void assertSuccessfulMigrations() {
        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version IN ('1', '2')
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
                        "SELECT COUNT(*) FROM audit_logs",
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

    private void assertDuplicateProcessedEventRejected() {
        insertProcessedEvent(EVENT_ID);
        assertThrows(
                DataAccessException.class,
                () -> insertProcessedEvent(EVENT_ID)
        );
    }

    private void assertDuplicateAuditEventRejected() {
        insertAuditEvent(EVENT_ID);
        assertThrows(
                DataAccessException.class,
                () -> insertAuditEvent(EVENT_ID)
        );
    }

    private void insertAuditEvent(String eventId) {
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
                ) VALUES (
                    ?,
                    'FLAG_UPDATED',
                    'fresh-checkout',
                    'DEV',
                    '2026-08-26T12:00:00',
                    'flag-service',
                    '2026-08-26 12:00:00.000000'
                )
                """,
                eventId
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
