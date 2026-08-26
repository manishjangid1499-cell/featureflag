package com.featureflag.notification_service.migration;

import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.entity.ProcessedEvent;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class NotificationLegacyUpgradeMySqlIT {

    private static final LocalDateTime DURABLE_NEXT_ATTEMPT =
            LocalDateTime.of(2026, 8, 26, 11, 0);

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withInitScript(
                            "db/legacy/notification_pre_flyway.sql"
                    );

    @Test
    void verifiedLegacySchemaBaselinesMigratesAndValidates() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();

        NotificationMigrationSchemaAssertions
                .assertPreFlywayLegacySchema(jdbcTemplate);
        String checkBefore = NotificationMigrationSchemaAssertions
                .deliveryModeCheckClause(jdbcTemplate);
        assertLegacyRowsBeforeMigration(jdbcTemplate);

        Flyway flyway = Flyway.configure()
                .dataSource(
                        MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword()
                )
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        MigrateResult firstMigration = flyway.migrate();

        assertEquals(3, firstMigration.migrationsExecuted);
        assertBaselineHistory(jdbcTemplate);
        NotificationMigrationSchemaAssertions.assertMigratedSchema(
                jdbcTemplate
        );
        assertEquals(
                checkBefore,
                NotificationMigrationSchemaAssertions
                        .deliveryModeCheckClause(jdbcTemplate)
        );
        assertLegacyRowsAfterMigration(jdbcTemplate);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertHibernateValidate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    private JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private void assertLegacyRowsBeforeMigration(
            JdbcTemplate jdbcTemplate
    ) {
        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM notifications",
                        Integer.class
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM notifications
                        WHERE id = 101
                          AND delivery_mode IS NULL
                          AND attempt_count IS NULL
                        """,
                        Integer.class
                )
        );
    }

    private void assertBaselineHistory(JdbcTemplate jdbcTemplate) {
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version = '1'
                          AND type = 'BASELINE'
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
                        WHERE version = '1'
                          AND type = 'SQL'
                        """,
                        Integer.class
                )
        );
        for (String version : new String[]{"2", "3", "4"}) {
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
    }

    private void assertLegacyRowsAfterMigration(
            JdbcTemplate jdbcTemplate
    ) {
        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM notifications",
                        Integer.class
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM notifications
                        WHERE id = 101
                          AND delivery_mode = 'SYNCHRONOUS'
                          AND attempt_count = 0
                          AND status = 'SENT'
                          AND sent_at = '2026-08-20 10:01:00.000000'
                        """,
                        Integer.class
                )
        );
        assertEquals(
                "DURABLE",
                jdbcTemplate.queryForObject(
                        """
                        SELECT delivery_mode
                        FROM notifications
                        WHERE id = 102
                          AND status = 'PENDING'
                          AND next_attempt_at = ?
                        """,
                        String.class,
                        Timestamp.valueOf(DURABLE_NEXT_ATTEMPT)
                )
        );
        assertEquals(
                "SMS",
                jdbcTemplate.queryForObject(
                        """
                        SELECT type
                        FROM notifications
                        WHERE id = 103
                        """,
                        String.class
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM processed_kafka_events
                        WHERE event_id = 'legacy-event-001'
                          AND topic = 'notification-events'
                        """,
                        Integer.class
                )
        );
    }

    private void assertHibernateValidate() {
        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(
                             LegacyValidationApplication.class
                     )
                             .web(WebApplicationType.NONE)
                             .run(
                                     "--spring.profiles.active=test",
                                     "--spring.datasource.url="
                                             + MYSQL.getJdbcUrl(),
                                     "--spring.datasource.username="
                                             + MYSQL.getUsername(),
                                     "--spring.datasource.password="
                                             + MYSQL.getPassword(),
                                     "--spring.datasource.driver-class-name="
                                             + "com.mysql.cj.jdbc.Driver",
                                     "--spring.jpa.database-platform="
                                             + "org.hibernate.dialect.MySQLDialect",
                                     "--spring.jpa.hibernate.ddl-auto=validate",
                                     "--spring.flyway.enabled=false",
                                     "--spring.main.banner-mode=off"
                             )) {
            assertNotNull(context.getBean(EntityManagerFactory.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {
            Notification.class,
            ProcessedEvent.class
    })
    static class LegacyValidationApplication {
    }
}
