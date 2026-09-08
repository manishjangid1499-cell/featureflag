package com.featureflag.auth_service.migration;

import com.featureflag.auth_service.entity.Invitation;
import com.featureflag.auth_service.entity.User;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class AuthLegacyUpgradeMySqlIT {

    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 8, 20, 10, 0);
    private static final LocalDateTime EXPIRES_AT =
            LocalDateTime.of(2099, 8, 20, 10, 0);

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withInitScript("db/legacy/auth_pre_flyway.sql");

    @Test
    void verifiedLegacySchemaBaselinesAndValidates() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();

        AuthMigrationSchemaAssertions.assertPreFlywayLegacySchema(
                jdbcTemplate
        );
        assertLegacyRowsBeforeBaseline(jdbcTemplate);
        String passwordBefore = passwordHash(jdbcTemplate);
        String tokenBefore = tokenHash(jdbcTemplate);

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

        assertEquals(2, firstMigration.migrationsExecuted);
        assertBaselineHistory(jdbcTemplate);
        AuthMigrationSchemaAssertions.assertMigratedSchema(jdbcTemplate);
        assertLegacyRowsAfterBaseline(jdbcTemplate);
        assertSecretValuePreserved(passwordBefore, passwordHash(jdbcTemplate));
        assertSecretValuePreserved(tokenBefore, tokenHash(jdbcTemplate));
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
                        "SELECT COUNT(*) FROM users",
                        Integer.class
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM invitations",
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
        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version IN ('2', '3')
                          AND type = 'SQL'
                          AND success = 1
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
                        FROM users
                        WHERE id = 101
                          AND email = 'owner@example.test'
                          AND name = 'Fixture Owner'
                          AND role = 'OWNER'
                          AND enabled = b'1'
                          AND version = 0
                        """,
                        Integer.class
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM invitations
                        WHERE id = 201
                          AND accepted_at IS NULL
                          AND created_at = ?
                          AND expires_at = ?
                          AND invited_by_user_id = 101
                          AND email = 'invitee@example.test'
                          AND full_name = 'Fixture Invitee'
                          AND invited_by_email = 'owner@example.test'
                          AND invited_by_name = 'Fixture Owner'
                          AND invited_role = 'DEVELOPER'
                          AND status = 'PENDING'
                        """,
                        Integer.class,
                        Timestamp.valueOf(CREATED_AT),
                        Timestamp.valueOf(EXPIRES_AT)
                )
        );
    }

    private String passwordHash(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE id = 101",
                String.class
        );
    }

    private String tokenHash(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject(
                "SELECT token_hash FROM invitations WHERE id = 201",
                String.class
        );
    }

    private void assertSecretValuePreserved(
            String before,
            String after
    ) {
        assertTrue(MessageDigest.isEqual(
                before.getBytes(StandardCharsets.UTF_8),
                after.getBytes(StandardCharsets.UTF_8)
        ));
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
                                     "--app.bootstrap.owner.enabled=false",
                                     "--spring.main.banner-mode=off"
                             )) {
            assertNotNull(context.getBean(EntityManagerFactory.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {User.class, Invitation.class})
    static class LegacyValidationApplication {
    }
}
