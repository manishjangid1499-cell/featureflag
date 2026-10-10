package com.featureflag.auth_service.migration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuthMigrationSchemaAssertions {

    static final String USER_EMAIL_UNIQUE =
            "UK6dotkott2kjsp8vw4d0m25fb7";
    static final String INVITATION_TOKEN_UNIQUE =
            "UK6a03cl7cgxqwekvwbi3dmdruk";
    static final String ROLE_ENUM =
            "enum('ADMIN','DEVELOPER','OWNER','VIEWER')";
    static final String INVITATION_STATUS_ENUM =
            "enum('ACCEPTED','EXPIRED','PENDING','REVOKED')";

    private AuthMigrationSchemaAssertions() {
    }

    static void assertPreFlywayLegacySchema(JdbcTemplate jdbcTemplate) {
        assertFalse(tableExists(jdbcTemplate, "flyway_schema_history"));
        assertBaseSchema(jdbcTemplate, false);
    }

    static void assertMigratedSchema(JdbcTemplate jdbcTemplate) {
        assertTrue(tableExists(jdbcTemplate, "flyway_schema_history"));
        assertBaseSchema(jdbcTemplate, true);
        assertPasswordResetsSchema(jdbcTemplate);
        assertSystemInvariantsSchema(jdbcTemplate);
    }

    private static void assertBaseSchema(
            JdbcTemplate jdbcTemplate,
            boolean migratedExpected
    ) {
        Set<String> expectedTables = migratedExpected
                ? Set.of("users", "invitations", "password_resets", "system_invariants")
                : Set.of("users", "invitations");

        assertEquals(
                expectedTables,
                Set.copyOf(jdbcTemplate.queryForList(
                        """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_type = 'BASE TABLE'
                          AND table_name <> 'flyway_schema_history'
                        """,
                        String.class
                ))
        );

        assertTable(jdbcTemplate, "users");
        assertTable(jdbcTemplate, "invitations");
        assertUserColumns(jdbcTemplate, migratedExpected);
        assertInvitationColumns(jdbcTemplate);
        assertIndexes(jdbcTemplate, migratedExpected);
        assertConstraints(jdbcTemplate, migratedExpected);
    }

    private static void assertUserColumns(
            JdbcTemplate jdbcTemplate,
            boolean migratedExpected
    ) {
        assertEquals(
                migratedExpected
                        ? List.of(
                                "id",
                                "email",
                                "name",
                                "password",
                                "role",
                                "enabled",
                                "version",
                                "credential_version"
                        )
                        : List.of(
                                "id",
                                "email",
                                "name",
                                "password",
                                "role"
                        ),
                columnNames(jdbcTemplate, "users")
        );

        assertColumn(jdbcTemplate, "users", "id",
                "bigint", "bigint", false, null, null,
                "auto_increment");
        assertColumn(jdbcTemplate, "users", "email",
                "varchar(255)", "varchar", false, 255L, null, "");
        assertColumn(jdbcTemplate, "users", "name",
                "varchar(255)", "varchar", true, 255L, null, "");
        assertColumn(jdbcTemplate, "users", "password",
                "varchar(255)", "varchar", false, 255L, null, "");
        assertColumn(jdbcTemplate, "users", "role",
                ROLE_ENUM, "enum", false, 9L, null, "");

        if (migratedExpected) {
            assertColumn(jdbcTemplate, "users", "version",
                    "bigint", "bigint", false, null, null, "");
            assertEquals("0", jdbcTemplate.queryForObject(
                    "SELECT column_default FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'users' "
                            + "AND column_name = 'version'", String.class));
            assertColumn(jdbcTemplate, "users", "enabled",
                    "bit(1)", "bit", false, null, null, "");
            assertEquals(
                    "b'1'",
                    jdbcTemplate.queryForObject(
                            """
                            SELECT column_default
                            FROM information_schema.columns
                            WHERE table_schema = DATABASE()
                              AND table_name = 'users'
                              AND column_name = 'enabled'
                            """,
                            String.class
                    )
            );
            assertColumn(jdbcTemplate, "users", "credential_version",
                    "bigint", "bigint", false, null, null, "");
            assertEquals("1", jdbcTemplate.queryForObject(
                    "SELECT column_default FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'users' "
                            + "AND column_name = 'credential_version'", String.class));
        }
    }

    private static void assertInvitationColumns(JdbcTemplate jdbcTemplate) {
        assertEquals(
                List.of(
                        "accepted_at",
                        "created_at",
                        "expires_at",
                        "id",
                        "invited_by_user_id",
                        "token_hash",
                        "email",
                        "full_name",
                        "invited_by_email",
                        "invited_by_name",
                        "invited_role",
                        "status"
                ),
                columnNames(jdbcTemplate, "invitations")
        );

        assertColumn(jdbcTemplate, "invitations", "accepted_at",
                "datetime(6)", "datetime", true, null, 6L, "");
        assertColumn(jdbcTemplate, "invitations", "created_at",
                "datetime(6)", "datetime", false, null, 6L, "");
        assertColumn(jdbcTemplate, "invitations", "expires_at",
                "datetime(6)", "datetime", false, null, 6L, "");
        assertColumn(jdbcTemplate, "invitations", "id",
                "bigint", "bigint", false, null, null, "auto_increment");
        assertColumn(jdbcTemplate, "invitations", "invited_by_user_id",
                "bigint", "bigint", true, null, null, "");
        assertColumn(jdbcTemplate, "invitations", "token_hash",
                "varchar(64)", "varchar", false, 64L, null, "");
        assertColumn(jdbcTemplate, "invitations", "email",
                "varchar(255)", "varchar", false, 255L, null, "");
        assertColumn(jdbcTemplate, "invitations", "full_name",
                "varchar(255)", "varchar", true, 255L, null, "");
        assertColumn(jdbcTemplate, "invitations", "invited_by_email",
                "varchar(255)", "varchar", true, 255L, null, "");
        assertColumn(jdbcTemplate, "invitations", "invited_by_name",
                "varchar(255)", "varchar", true, 255L, null, "");
        assertColumn(jdbcTemplate, "invitations", "invited_role",
                ROLE_ENUM, "enum", false, 9L, null, "");
        assertColumn(jdbcTemplate, "invitations", "status",
                INVITATION_STATUS_ENUM, "enum", false, 8L, null, "");
    }

    private static void assertPasswordResetsSchema(JdbcTemplate jdbcTemplate) {
        assertTable(jdbcTemplate, "password_resets");
        assertEquals(
                List.of(
                        "id",
                        "user_id",
                        "email",
                        "token_hash",
                        "credential_version",
                        "expires_at",
                        "status",
                        "created_at",
                        "used_at"
                ),
                columnNames(jdbcTemplate, "password_resets")
        );
        assertColumn(jdbcTemplate, "password_resets", "id",
                "bigint", "bigint", false, null, null, "auto_increment");
        assertColumn(jdbcTemplate, "password_resets", "user_id",
                "bigint", "bigint", false, null, null, "");
        assertColumn(jdbcTemplate, "password_resets", "email",
                "varchar(255)", "varchar", false, 255L, null, "");
        assertColumn(jdbcTemplate, "password_resets", "token_hash",
                "varchar(64)", "varchar", false, 64L, null, "");
        assertColumn(jdbcTemplate, "password_resets", "credential_version",
                "bigint", "bigint", false, null, null, "");
        assertColumn(jdbcTemplate, "password_resets", "expires_at",
                "datetime(6)", "datetime", false, null, 6L, "");
        assertColumn(jdbcTemplate, "password_resets", "status",
                "enum('PENDING','USED','EXPIRED','REVOKED')", "enum", false, 7L, null, "");
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT column_default FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'password_resets' "
                        + "AND column_name = 'status'", String.class));
        assertColumn(jdbcTemplate, "password_resets", "created_at",
                "datetime(6)", "datetime", false, null, 6L, "");
        assertColumn(jdbcTemplate, "password_resets", "used_at",
                "datetime(6)", "datetime", true, null, 6L, "");
    }

    private static void assertSystemInvariantsSchema(JdbcTemplate jdbcTemplate) {
        assertTable(jdbcTemplate, "system_invariants");
        assertEquals(
                List.of("name", "owner_user_id", "updated_at"),
                columnNames(jdbcTemplate, "system_invariants")
        );
        assertColumn(jdbcTemplate, "system_invariants", "name",
                "varchar(64)", "varchar", false, 64L, null, "");
        assertColumn(jdbcTemplate, "system_invariants", "owner_user_id",
                "bigint", "bigint", true, null, null, "");
        assertColumn(jdbcTemplate, "system_invariants", "updated_at",
                "datetime(6)", "datetime", false, null, 6L, "");
    }

    private static void assertIndexes(JdbcTemplate jdbcTemplate, boolean migratedExpected) {
        if (!migratedExpected) {
            assertEquals(
                    Set.of(
                            "users:PRIMARY",
                            "users:" + USER_EMAIL_UNIQUE,
                            "invitations:PRIMARY",
                            "invitations:" + INVITATION_TOKEN_UNIQUE
                    ),
                    Set.copyOf(jdbcTemplate.queryForList(
                            """
                            SELECT DISTINCT CONCAT(table_name, ':', index_name)
                            FROM information_schema.statistics
                            WHERE table_schema = DATABASE()
                              AND table_name IN ('users', 'invitations')
                            """,
                            String.class
                    ))
            );
        } else {
            assertEquals(
                    Set.of(
                            "users:PRIMARY",
                            "users:" + USER_EMAIL_UNIQUE,
                            "invitations:PRIMARY",
                            "invitations:" + INVITATION_TOKEN_UNIQUE,
                            "password_resets:PRIMARY",
                            "password_resets:uk_password_resets_token_hash",
                            "password_resets:idx_password_resets_user_id_status",
                            "password_resets:idx_password_resets_email_status",
                            "system_invariants:PRIMARY",
                            "system_invariants:fk_system_invariants_owner"
                    ),
                    Set.copyOf(jdbcTemplate.queryForList(
                            """
                            SELECT DISTINCT CONCAT(table_name, ':', index_name)
                            FROM information_schema.statistics
                            WHERE table_schema = DATABASE()
                              AND table_name IN ('users', 'invitations', 'password_resets', 'system_invariants')
                            """,
                            String.class
                    ))
            );
        }

        assertIndex(
                jdbcTemplate,
                "users",
                "PRIMARY",
                List.of("id"),
                true
        );
        assertIndex(
                jdbcTemplate,
                "users",
                USER_EMAIL_UNIQUE,
                List.of("email"),
                true
        );
        assertIndex(
                jdbcTemplate,
                "invitations",
                "PRIMARY",
                List.of("id"),
                true
        );
        assertIndex(
                jdbcTemplate,
                "invitations",
                INVITATION_TOKEN_UNIQUE,
                List.of("token_hash"),
                true
        );
    }

    private static void assertConstraints(JdbcTemplate jdbcTemplate, boolean migratedExpected) {
        if (!migratedExpected) {
            assertEquals(
                    Set.of(
                            "users:PRIMARY:PRIMARY KEY",
                            "users:" + USER_EMAIL_UNIQUE + ":UNIQUE",
                            "invitations:PRIMARY:PRIMARY KEY",
                            "invitations:" + INVITATION_TOKEN_UNIQUE + ":UNIQUE"
                    ),
                    Set.copyOf(jdbcTemplate.queryForList(
                            """
                            SELECT CONCAT(
                                table_name,
                                ':',
                                constraint_name,
                                ':',
                                constraint_type
                            )
                            FROM information_schema.table_constraints
                            WHERE constraint_schema = DATABASE()
                              AND table_name IN ('users', 'invitations')
                            """,
                            String.class
                    ))
            );
            assertEquals(
                    0,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM information_schema.key_column_usage
                            WHERE constraint_schema = DATABASE()
                              AND referenced_table_name IS NOT NULL
                            """,
                            Integer.class
                    )
            );
        } else {
            assertEquals(
                    Set.of(
                            "users:PRIMARY:PRIMARY KEY",
                            "users:" + USER_EMAIL_UNIQUE + ":UNIQUE",
                            "invitations:PRIMARY:PRIMARY KEY",
                            "invitations:" + INVITATION_TOKEN_UNIQUE + ":UNIQUE",
                            "password_resets:PRIMARY:PRIMARY KEY",
                            "password_resets:uk_password_resets_token_hash:UNIQUE",
                            "system_invariants:PRIMARY:PRIMARY KEY",
                            "system_invariants:fk_system_invariants_owner:FOREIGN KEY"
                    ),
                    Set.copyOf(jdbcTemplate.queryForList(
                            """
                            SELECT CONCAT(
                                table_name,
                                ':',
                                constraint_name,
                                ':',
                                constraint_type
                            )
                            FROM information_schema.table_constraints
                            WHERE constraint_schema = DATABASE()
                              AND table_name IN ('users', 'invitations', 'password_resets', 'system_invariants')
                            """,
                            String.class
                    ))
            );
            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM information_schema.key_column_usage
                            WHERE constraint_schema = DATABASE()
                              AND constraint_name = 'fk_system_invariants_owner'
                              AND referenced_table_name = 'users'
                              AND referenced_column_name = 'id'
                            """,
                            Integer.class
                    )
            );
        }
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.check_constraints
                        WHERE constraint_schema = DATABASE()
                        """,
                        Integer.class
                )
        );
    }

    private static void assertTable(
            JdbcTemplate jdbcTemplate,
            String tableName
    ) {
        TableMetadata metadata = jdbcTemplate.queryForObject(
                """
                SELECT engine, table_collation
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """,
                (resultSet, rowNumber) -> new TableMetadata(
                        resultSet.getString("engine"),
                        resultSet.getString("table_collation")
                ),
                tableName
        );
        assertNotNull(metadata);
        assertEquals("InnoDB", metadata.engine());
        assertEquals("utf8mb4_0900_ai_ci", metadata.collation());
    }

    private static void assertColumn(
            JdbcTemplate jdbcTemplate,
            String tableName,
            String columnName,
            String columnType,
            String dataType,
            boolean nullable,
            Long length,
            Long datetimePrecision,
            String extra
    ) {
        ColumnMetadata metadata = jdbcTemplate.queryForObject(
                """
                SELECT column_type,
                       data_type,
                       is_nullable,
                       character_maximum_length,
                       datetime_precision,
                       extra
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                (resultSet, rowNumber) -> new ColumnMetadata(
                        resultSet.getString("column_type"),
                        resultSet.getString("data_type"),
                        "YES".equals(resultSet.getString("is_nullable")),
                        nullableLong(resultSet.getObject(
                                "character_maximum_length"
                        )),
                        nullableLong(resultSet.getObject(
                                "datetime_precision"
                        )),
                        resultSet.getString("extra")
                ),
                tableName,
                columnName
        );
        assertNotNull(metadata);
        assertEquals(columnType, metadata.columnType());
        assertEquals(dataType, metadata.dataType());
        assertEquals(nullable, metadata.nullable());
        assertEquals(length, metadata.length());
        assertEquals(datetimePrecision, metadata.datetimePrecision());
        assertEquals(extra, metadata.extra());
    }

    private static void assertIndex(
            JdbcTemplate jdbcTemplate,
            String tableName,
            String indexName,
            List<String> expectedColumns,
            boolean unique
    ) {
        List<IndexMetadata> metadata = jdbcTemplate.query(
                """
                SELECT column_name, non_unique
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                ORDER BY seq_in_index
                """,
                (resultSet, rowNumber) -> new IndexMetadata(
                        resultSet.getString("column_name"),
                        resultSet.getBoolean("non_unique")
                ),
                tableName,
                indexName
        );
        assertEquals(
                expectedColumns,
                metadata.stream().map(IndexMetadata::columnName).toList()
        );
        assertTrue(metadata.stream().allMatch(
                index -> index.nonUnique() != unique
        ));
    }

    private static List<String> columnNames(
            JdbcTemplate jdbcTemplate,
            String tableName
    ) {
        return jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                ORDER BY ordinal_position
                """,
                String.class,
                tableName
        );
    }

    private static boolean tableExists(
            JdbcTemplate jdbcTemplate,
            String tableName
    ) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) > 0
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """,
                Boolean.class,
                tableName
        );
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record ColumnMetadata(
            String columnType,
            String dataType,
            boolean nullable,
            Long length,
            Long datetimePrecision,
            String extra
    ) {
    }

    private record IndexMetadata(
            String columnName,
            boolean nonUnique
    ) {
    }

    private record TableMetadata(
            String engine,
            String collation
    ) {
    }
}
