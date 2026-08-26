package com.featureflag.flag_service.migration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FlagMigrationSchemaAssertions {

    static final String FLAG_UNIQUE = "uk_flag_key_environment";
    static final String TARGET_USER_FOREIGN_KEY =
            "FKa9lsvqrrunroyjdceekdqrj6l";
    static final String OUTBOX_DUE_INDEX =
            "idx_outbox_status_next_attempt";
    static final String OUTBOX_CREATED_INDEX = "idx_outbox_created_at";

    private static final Set<String> BUSINESS_TABLES = Set.of(
            "feature_flags",
            "flag_target_users",
            "outbox_events"
    );

    private FlagMigrationSchemaAssertions() {
    }

    static void assertPreFlywayLegacySchema(JdbcTemplate jdbcTemplate) {
        assertFalse(tableExists(jdbcTemplate, "flyway_schema_history"));
        assertBaseSchema(jdbcTemplate);
    }

    static void assertMigratedSchema(JdbcTemplate jdbcTemplate) {
        assertTrue(tableExists(jdbcTemplate, "flyway_schema_history"));
        assertBaseSchema(jdbcTemplate);
    }

    private static void assertBaseSchema(JdbcTemplate jdbcTemplate) {
        assertEquals(
                BUSINESS_TABLES,
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

        BUSINESS_TABLES.forEach(
                tableName -> assertTable(jdbcTemplate, tableName)
        );
        assertFeatureFlagColumns(jdbcTemplate);
        assertTargetUserColumns(jdbcTemplate);
        assertOutboxColumns(jdbcTemplate);
        assertIndexes(jdbcTemplate);
        assertConstraints(jdbcTemplate);
    }

    private static void assertFeatureFlagColumns(
            JdbcTemplate jdbcTemplate
    ) {
        assertEquals(
                List.of(
                        "enabled",
                        "rollout_percentage",
                        "end_date",
                        "id",
                        "start_date",
                        "description",
                        "environment",
                        "flag_key",
                        "name"
                ),
                columnNames(jdbcTemplate, "feature_flags")
        );

        assertColumn(jdbcTemplate, "feature_flags", "enabled",
                "bit(1)", "bit", true, null, null, "");
        assertColumn(jdbcTemplate, "feature_flags", "rollout_percentage",
                "int", "int", true, null, null, "");
        assertColumn(jdbcTemplate, "feature_flags", "end_date",
                "datetime(6)", "datetime", true, null, 6L, "");
        assertColumn(jdbcTemplate, "feature_flags", "id",
                "bigint", "bigint", false, null, null,
                "auto_increment");
        assertColumn(jdbcTemplate, "feature_flags", "start_date",
                "datetime(6)", "datetime", true, null, 6L, "");
        assertColumn(jdbcTemplate, "feature_flags", "description",
                "varchar(255)", "varchar", true, 255L, null, "");
        assertColumn(jdbcTemplate, "feature_flags", "environment",
                "varchar(255)", "varchar", false, 255L, null, "");
        assertColumn(jdbcTemplate, "feature_flags", "flag_key",
                "varchar(255)", "varchar", false, 255L, null, "");
        assertColumn(jdbcTemplate, "feature_flags", "name",
                "varchar(255)", "varchar", true, 255L, null, "");
    }

    private static void assertTargetUserColumns(
            JdbcTemplate jdbcTemplate
    ) {
        assertEquals(
                List.of("flag_id", "user_id"),
                columnNames(jdbcTemplate, "flag_target_users")
        );

        assertColumn(jdbcTemplate, "flag_target_users", "flag_id",
                "bigint", "bigint", false, null, null, "");
        assertColumn(jdbcTemplate, "flag_target_users", "user_id",
                "varchar(255)", "varchar", true, 255L, null, "");
    }

    private static void assertOutboxColumns(JdbcTemplate jdbcTemplate) {
        assertEquals(
                List.of(
                        "attempts",
                        "created_at",
                        "next_attempt_at",
                        "published_at",
                        "status",
                        "id",
                        "event_type",
                        "topic",
                        "last_error_type",
                        "message_key",
                        "payload"
                ),
                columnNames(jdbcTemplate, "outbox_events")
        );

        assertColumn(jdbcTemplate, "outbox_events", "attempts",
                "int", "int", false, null, null, "");
        assertColumn(jdbcTemplate, "outbox_events", "created_at",
                "datetime(6)", "datetime", false, null, 6L, "");
        assertColumn(jdbcTemplate, "outbox_events", "next_attempt_at",
                "datetime(6)", "datetime", false, null, 6L, "");
        assertColumn(jdbcTemplate, "outbox_events", "published_at",
                "datetime(6)", "datetime", true, null, 6L, "");
        assertColumn(jdbcTemplate, "outbox_events", "status",
                "varchar(20)", "varchar", false, 20L, null, "");
        assertColumn(jdbcTemplate, "outbox_events", "id",
                "varchar(36)", "varchar", false, 36L, null, "");
        assertColumn(jdbcTemplate, "outbox_events", "event_type",
                "varchar(100)", "varchar", false, 100L, null, "");
        assertColumn(jdbcTemplate, "outbox_events", "topic",
                "varchar(120)", "varchar", false, 120L, null, "");
        assertColumn(jdbcTemplate, "outbox_events", "last_error_type",
                "varchar(255)", "varchar", true, 255L, null, "");
        assertColumn(jdbcTemplate, "outbox_events", "message_key",
                "varchar(255)", "varchar", true, 255L, null, "");
        assertColumn(jdbcTemplate, "outbox_events", "payload",
                "longtext", "longtext", false, 4294967295L, null, "");
    }

    private static void assertIndexes(JdbcTemplate jdbcTemplate) {
        assertEquals(
                Set.of(
                        "feature_flags:PRIMARY",
                        "feature_flags:" + FLAG_UNIQUE,
                        "flag_target_users:" + TARGET_USER_FOREIGN_KEY,
                        "outbox_events:PRIMARY",
                        "outbox_events:" + OUTBOX_DUE_INDEX,
                        "outbox_events:" + OUTBOX_CREATED_INDEX
                ),
                Set.copyOf(jdbcTemplate.queryForList(
                        """
                        SELECT DISTINCT CONCAT(table_name, ':', index_name)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name IN (
                              'feature_flags',
                              'flag_target_users',
                              'outbox_events'
                          )
                        """,
                        String.class
                ))
        );

        assertIndex(jdbcTemplate, "feature_flags", "PRIMARY",
                List.of("id"), true);
        assertIndex(jdbcTemplate, "feature_flags", FLAG_UNIQUE,
                List.of("flag_key", "environment"), true);
        assertIndex(jdbcTemplate, "flag_target_users",
                TARGET_USER_FOREIGN_KEY, List.of("flag_id"), false);
        assertIndex(jdbcTemplate, "outbox_events", "PRIMARY",
                List.of("id"), true);
        assertIndex(jdbcTemplate, "outbox_events", OUTBOX_DUE_INDEX,
                List.of("status", "next_attempt_at"), false);
        assertIndex(jdbcTemplate, "outbox_events", OUTBOX_CREATED_INDEX,
                List.of("created_at"), false);
    }

    private static void assertConstraints(JdbcTemplate jdbcTemplate) {
        assertEquals(
                Set.of(
                        "feature_flags:PRIMARY:PRIMARY KEY",
                        "feature_flags:" + FLAG_UNIQUE + ":UNIQUE",
                        "flag_target_users:" + TARGET_USER_FOREIGN_KEY
                                + ":FOREIGN KEY",
                        "outbox_events:PRIMARY:PRIMARY KEY"
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
                          AND table_name IN (
                              'feature_flags',
                              'flag_target_users',
                              'outbox_events'
                          )
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
                          AND table_name = 'flag_target_users'
                          AND constraint_name = ?
                          AND column_name = 'flag_id'
                          AND referenced_table_name = 'feature_flags'
                          AND referenced_column_name = 'id'
                        """,
                        Integer.class,
                        TARGET_USER_FOREIGN_KEY
                )
        );

        ReferentialConstraint foreignKey = jdbcTemplate.queryForObject(
                """
                SELECT update_rule, delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'flag_target_users'
                  AND constraint_name = ?
                """,
                (resultSet, rowNumber) -> new ReferentialConstraint(
                        resultSet.getString("update_rule"),
                        resultSet.getString("delete_rule")
                ),
                TARGET_USER_FOREIGN_KEY
        );
        assertNotNull(foreignKey);
        assertEquals("NO ACTION", foreignKey.updateRule());
        assertEquals("NO ACTION", foreignKey.deleteRule());

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints
                        WHERE constraint_schema = DATABASE()
                          AND table_name IN (
                              'feature_flags',
                              'flag_target_users',
                              'outbox_events'
                          )
                          AND constraint_type = 'CHECK'
                        """,
                        Integer.class
                )
        );
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name IN (
                              'feature_flags',
                              'flag_target_users',
                              'outbox_events'
                          )
                          AND data_type = 'enum'
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
                SELECT tables.engine,
                       tables.table_collation,
                       collations.character_set_name
                FROM information_schema.tables AS tables
                JOIN information_schema.collation_character_set_applicability
                  AS collations
                  ON collations.collation_name = tables.table_collation
                WHERE tables.table_schema = DATABASE()
                  AND tables.table_name = ?
                """,
                (resultSet, rowNumber) -> new TableMetadata(
                        resultSet.getString("engine"),
                        resultSet.getString("table_collation"),
                        resultSet.getString("character_set_name")
                ),
                tableName
        );
        assertNotNull(metadata);
        assertEquals("InnoDB", metadata.engine());
        assertEquals("utf8mb4_0900_ai_ci", metadata.collation());
        assertEquals("utf8mb4", metadata.characterSet());
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

    private record ReferentialConstraint(
            String updateRule,
            String deleteRule
    ) {
    }

    private record TableMetadata(
            String engine,
            String collation,
            String characterSet
    ) {
    }
}
