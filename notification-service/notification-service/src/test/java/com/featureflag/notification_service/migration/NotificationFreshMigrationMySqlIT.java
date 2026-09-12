package com.featureflag.notification_service.migration;

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
class NotificationFreshMigrationMySqlIT {

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
        assertSuccessfulSqlMigration("1");
        assertSuccessfulSqlMigration("2");
        assertSuccessfulSqlMigration("3");
        assertSuccessfulSqlMigration("4");
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

        NotificationMigrationSchemaAssertions.assertMigratedSchema(
                jdbcTemplate
        );
        assertDeliveryModeCheckBehavior();

        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    private void assertSuccessfulSqlMigration(String version) {
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version = ?
                          AND type = 'SQL'
                          AND success = 1
                        """,
                        Integer.class,
                        version
                )
        );
    }

    private void assertDeliveryModeCheckBehavior() {
        insertNotification("fresh-null@example.test", null);
        insertNotification("fresh-sync@example.test", "SYNCHRONOUS");
        insertNotification("fresh-durable@example.test", "DURABLE");

        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM notifications
                        WHERE recipient LIKE 'fresh-%@example.test'
                        """,
                        Integer.class
                )
        );
        assertThrows(
                DataAccessException.class,
                () -> insertNotification(
                        "fresh-invalid@example.test",
                        "UNSUPPORTED"
                )
        );
    }

    private void insertNotification(
            String recipient,
            String deliveryMode
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO notifications (
                    attempt_count,
                    created_at,
                    delivery_mode,
                    message,
                    recipient,
                    status,
                    subject,
                    type
                ) VALUES (0, '2026-08-26 12:00:00.000000', ?,
                          'Message', ?, 'PENDING', 'Subject', 'EMAIL')
                """,
                deliveryMode,
                recipient
        );
    }
}
