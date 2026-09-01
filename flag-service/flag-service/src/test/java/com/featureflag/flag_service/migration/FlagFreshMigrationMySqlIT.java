package com.featureflag.flag_service.migration;

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
class FlagFreshMigrationMySqlIT {

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
        FlagMigrationSchemaAssertions.assertMigratedSchema(jdbcTemplate);
        assertNoBootstrapRows();
        assertFlagUniqueConstraintRejectsDuplicate();
        assertTargetUserForeignKeyRejectsMissingFlag();
        assertSdkKeyHashRejectsDuplicate();
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
                        "SELECT COUNT(*) FROM feature_flags",
                        Integer.class
                )
        );
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flag_target_users",
                        Integer.class
                )
        );
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_events",
                        Integer.class
                )
        );
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sdk_keys",
                        Integer.class
                )
        );
    }

    private void assertFlagUniqueConstraintRejectsDuplicate() {
        insertFlag("fresh-checkout", "DEV");
        assertThrows(
                DataAccessException.class,
                () -> insertFlag("fresh-checkout", "DEV")
        );
    }

    private void assertTargetUserForeignKeyRejectsMissingFlag() {
        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO flag_target_users (flag_id, user_id)
                        VALUES (999999, 'missing-flag-user')
                        """
                )
        );
    }

    private void assertSdkKeyHashRejectsDuplicate() {
        insertSdkKey(1L, "a".repeat(64));
        assertThrows(
                DataAccessException.class,
                () -> insertSdkKey(2L, "a".repeat(64))
        );
    }

    private void insertSdkKey(Long id, String keyHash) {
        jdbcTemplate.update(
                """
                INSERT INTO sdk_keys (
                    active,
                    created_at,
                    id,
                    environment,
                    key_prefix,
                    name,
                    key_hash,
                    created_by
                ) VALUES (
                    b'1',
                    '2026-08-27 12:00:00.000000',
                    ?,
                    'DEV',
                    'ff_sdk_fixture',
                    'Fixture key',
                    ?,
                    'fixture-actor'
                )
                """,
                id,
                keyHash
        );
    }

    private void insertFlag(String flagKey, String environment) {
        jdbcTemplate.update(
                """
                INSERT INTO feature_flags (flag_key, environment)
                VALUES (?, ?)
                """,
                flagKey,
                environment
        );
    }
}
