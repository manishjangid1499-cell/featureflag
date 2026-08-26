package com.featureflag.auth_service.migration;

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
class AuthFreshMigrationMySqlIT {

    private static final String TOKEN_HASH = "b".repeat(64);

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
        AuthMigrationSchemaAssertions.assertMigratedSchema(jdbcTemplate);
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users",
                        Integer.class
                )
        );
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM invitations",
                        Integer.class
                )
        );

        assertUniqueConstraintsRejectDuplicates();

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

    private void assertUniqueConstraintsRejectDuplicates() {
        insertUser("member@example.test");
        assertThrows(
                DataAccessException.class,
                () -> insertUser("member@example.test")
        );

        insertInvitation("first@example.test", TOKEN_HASH);
        assertThrows(
                DataAccessException.class,
                () -> insertInvitation(
                        "second@example.test",
                        TOKEN_HASH
                )
        );
    }

    private void insertUser(String email) {
        jdbcTemplate.update(
                """
                INSERT INTO users (email, name, password, role)
                VALUES (?, 'Fixture Member', 'test-password-hash', 'VIEWER')
                """,
                email
        );
    }

    private void insertInvitation(String email, String tokenHash) {
        jdbcTemplate.update(
                """
                INSERT INTO invitations (
                    accepted_at,
                    created_at,
                    expires_at,
                    invited_by_user_id,
                    token_hash,
                    email,
                    full_name,
                    invited_by_email,
                    invited_by_name,
                    invited_role,
                    status
                ) VALUES (
                    NULL,
                    '2026-08-26 12:00:00.000000',
                    '2099-08-26 12:00:00.000000',
                    NULL,
                    ?,
                    ?,
                    'Fixture Invitee',
                    NULL,
                    NULL,
                    'DEVELOPER',
                    'PENDING'
                )
                """,
                tokenHash,
                email
        );
    }
}
