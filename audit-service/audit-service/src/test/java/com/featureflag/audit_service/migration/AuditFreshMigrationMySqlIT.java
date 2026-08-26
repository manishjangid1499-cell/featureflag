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
        assertSuccessfulV1();
        AuditMigrationSchemaAssertions.assertMigratedSchema(jdbcTemplate);
        assertNoBootstrapRows();
        assertDuplicateProcessedEventRejected();
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    private void assertSuccessfulV1() {
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
