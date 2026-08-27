package com.featureflag.analytics_service.migration;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.entity.ProcessedEvent;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
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
class AnalyticsLegacyUpgradeMySqlIT {

    private static final LocalDateTime PROCESSED_AT =
            LocalDateTime.of(
                    2026,
                    8,
                    20,
                    10,
                    1,
                    2,
                    123_456_000
            );

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withInitScript(
                            "db/legacy/analytics_pre_flyway.sql"
                    );

    @Test
    void verifiedLegacySchemaBaselinesAndValidates() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();

        AnalyticsMigrationSchemaAssertions.assertPreFlywayLegacySchema(
                jdbcTemplate
        );
        assertLegacyRowsBeforeBaseline(jdbcTemplate);

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

        assertEquals(0, firstMigration.migrationsExecuted);
        assertBaselineHistory(jdbcTemplate);
        AnalyticsMigrationSchemaAssertions.assertMigratedSchema(
                jdbcTemplate
        );
        assertLegacyRowsAfterBaseline(jdbcTemplate);
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

    private void assertLegacyRowsBeforeBaseline(
            JdbcTemplate jdbcTemplate
    ) {
        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM analytics_events",
                        Integer.class
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM processed_kafka_events",
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
    }

    private void assertLegacyRowsAfterBaseline(
            JdbcTemplate jdbcTemplate
    ) {
        assertEquals(
                1,
                matchingAggregateCount(jdbcTemplate, 101L, 5L)
        );
        assertEquals(
                1,
                matchingAggregateCount(jdbcTemplate, 102L, 3L)
        );
        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM analytics_events
                        WHERE flag_key = 'fixture-checkout'
                          AND environment = 'DEV'
                          AND event_type = 'FLAG_EVALUATED'
                        """,
                        Integer.class
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM analytics_events
                        WHERE id = 103
                          AND count IS NULL
                          AND environment IS NULL
                          AND event_type IS NULL
                          AND flag_key IS NULL
                        """,
                        Integer.class
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM processed_kafka_events
                        WHERE event_id = 'legacy-analytics-event-001'
                          AND topic = 'feature-flag-events'
                          AND processed_at = ?
                        """,
                        Integer.class,
                        Timestamp.valueOf(PROCESSED_AT)
                )
        );
        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM analytics_events",
                        Integer.class
                )
        );
    }

    private int matchingAggregateCount(
            JdbcTemplate jdbcTemplate,
            long id,
            long count
    ) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM analytics_events
                WHERE id = ?
                  AND count = ?
                  AND environment = 'DEV'
                  AND event_type = 'FLAG_EVALUATED'
                  AND flag_key = 'fixture-checkout'
                """,
                Integer.class,
                id,
                count
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
                                     "--eureka.client.enabled=false",
                                     "--spring.cloud.discovery.enabled=false",
                                     "--spring.main.banner-mode=off"
                             )) {
            assertNotNull(context.getBean(EntityManagerFactory.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {
            AnalyticsEvent.class,
            ProcessedEvent.class
    })
    static class LegacyValidationApplication {
    }
}
