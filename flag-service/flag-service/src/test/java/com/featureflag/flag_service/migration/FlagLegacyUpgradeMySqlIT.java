package com.featureflag.flag_service.migration;

import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.entity.OutboxEvent;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class FlagLegacyUpgradeMySqlIT {

    private static final LocalDateTime FLAG_START =
            LocalDateTime.of(2026, 8, 20, 10, 0);
    private static final LocalDateTime FLAG_END =
            LocalDateTime.of(2099, 8, 20, 10, 0);

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withInitScript("db/legacy/flag_pre_flyway.sql");

    @Test
    void verifiedLegacySchemaBaselinesAndValidates() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();

        FlagMigrationSchemaAssertions.assertPreFlywayLegacySchema(
                jdbcTemplate
        );
        assertLegacyRowsBeforeBaseline(jdbcTemplate);
        Map<String, String> payloadDigestsBefore =
                payloadDigests(jdbcTemplate);

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
        FlagMigrationSchemaAssertions.assertMigratedSchema(jdbcTemplate);
        assertLegacyRowsAfterBaseline(jdbcTemplate);
        assertEquals(payloadDigestsBefore, payloadDigests(jdbcTemplate));
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
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM feature_flags",
                        Integer.class
                )
        );
        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flag_target_users",
                        Integer.class
                )
        );
        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_events",
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
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM feature_flags
                        WHERE id = 101
                          AND enabled = b'1'
                          AND rollout_percentage = 50
                          AND end_date = ?
                          AND start_date = ?
                          AND description = 'Fixture checkout flag'
                          AND environment = 'DEV'
                          AND flag_key = 'fixture-checkout'
                          AND name = 'Fixture Checkout'
                        """,
                        Integer.class,
                        Timestamp.valueOf(FLAG_END),
                        Timestamp.valueOf(FLAG_START)
                )
        );
        assertEquals(
                List.of("fixture-user-a", "fixture-user-b"),
                jdbcTemplate.queryForList(
                        """
                        SELECT user_id
                        FROM flag_target_users
                        WHERE flag_id = 101
                        ORDER BY user_id
                        """,
                        String.class
                )
        );

        assertPendingOutboxPreserved(jdbcTemplate);
        assertPublishedOutboxPreserved(jdbcTemplate);
        assertDeadOutboxPreserved(jdbcTemplate);
    }

    private void assertPendingOutboxPreserved(
            JdbcTemplate jdbcTemplate
    ) {
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM outbox_events
                        WHERE id = '11111111-1111-1111-1111-111111111111'
                          AND attempts = 2
                          AND created_at = '2026-08-20 11:00:00.000000'
                          AND next_attempt_at = '2099-08-20 11:00:00.000000'
                          AND published_at IS NULL
                          AND status = 'PENDING'
                          AND event_type = 'FLAG_UPDATED'
                          AND topic = 'feature-flag-events'
                          AND last_error_type = 'ExecutionException'
                          AND message_key = 'fixture-checkout'
                        """,
                        Integer.class
                )
        );
    }

    private void assertPublishedOutboxPreserved(
            JdbcTemplate jdbcTemplate
    ) {
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM outbox_events
                        WHERE id = '22222222-2222-2222-2222-222222222222'
                          AND attempts = 1
                          AND created_at = '2026-08-20 12:00:00.000000'
                          AND next_attempt_at = '2026-08-20 12:00:00.000000'
                          AND published_at = '2026-08-20 12:01:00.000000'
                          AND status = 'PUBLISHED'
                          AND event_type = 'FLAG_CREATED'
                          AND topic = 'feature-flag-events'
                          AND last_error_type IS NULL
                          AND message_key = 'fixture-checkout'
                        """,
                        Integer.class
                )
        );
    }

    private void assertDeadOutboxPreserved(JdbcTemplate jdbcTemplate) {
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM outbox_events
                        WHERE id = '33333333-3333-3333-3333-333333333333'
                          AND attempts = 10
                          AND created_at = '2026-08-20 13:00:00.000000'
                          AND next_attempt_at = '2026-08-20 13:01:00.000000'
                          AND published_at IS NULL
                          AND status = 'DEAD'
                          AND event_type = 'NOTIFICATION'
                          AND topic = 'notification-events'
                          AND last_error_type = 'ExecutionException'
                          AND message_key =
                              '33333333-3333-3333-3333-333333333333'
                        """,
                        Integer.class
                )
        );
    }

    private Map<String, String> payloadDigests(
            JdbcTemplate jdbcTemplate
    ) {
        return jdbcTemplate.query(
                "SELECT id, payload FROM outbox_events",
                (resultSet, rowNumber) -> Map.entry(
                        resultSet.getString("id"),
                        sha256(resultSet.getString("payload"))
                )
        ).stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                Map.Entry::getValue
        ));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to hash fixture payload",
                    exception
            );
        }
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
            FeatureFlag.class,
            OutboxEvent.class
    })
    static class LegacyValidationApplication {
    }
}
