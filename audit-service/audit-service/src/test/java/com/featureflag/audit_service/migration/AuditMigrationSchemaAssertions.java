package com.featureflag.audit_service.migration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuditMigrationSchemaAssertions {

    private static final Set<String> BUSINESS_TABLES = Set.of(
            "audit_logs",
            "processed_kafka_events"
    );

    private AuditMigrationSchemaAssertions() {
    }

    static void assertPreFlywayLegacySchema(JdbcTemplate jdbcTemplate) {
        assertFalse(tableExists(jdbcTemplate, "flyway_schema_history"));
        assertSchema(jdbcTemplate, false);
    }

    static void assertMigratedSchema(JdbcTemplate jdbcTemplate) {
        assertTrue(tableExists(jdbcTemplate, "flyway_schema_history"));
        assertSchema(jdbcTemplate, true);
    }

    private static void assertSchema(
            JdbcTemplate jdbcTemplate,
            boolean enriched
    ) {
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
        assertAuditLogColumns(jdbcTemplate, enriched);
        assertProcessedEventColumns(jdbcTemplate);
        assertIndexes(jdbcTemplate, enriched);
        assertConstraints(jdbcTemplate, enriched);
    }

    private static void assertAuditLogColumns(
            JdbcTemplate jdbcTemplate,
            boolean enriched
    ) {
        List<String> expectedColumns = enriched
                ? List.of(
                        "id",
                        "environment",
                        "event_type",
                        "flag_key",
                        "timestamp",
                        "event_id",
                        "source_service",
                        "actor",
                        "before_state",
                        "after_state",
                        "occurred_at"
                )
                : List.of(
                        "id",
                        "environment",
                        "event_type",
                        "flag_key",
                        "timestamp"
                );

        assertEquals(
                expectedColumns,
                columnNames(jdbcTemplate, "audit_logs")
        );

        assertColumn(jdbcTemplate, "audit_logs", "id",
                "bigint", "bigint", false, null, null,
                "auto_increment");
        assertColumn(jdbcTemplate, "audit_logs", "environment",
                "varchar(255)", "varchar", true, 255L, null, "");
        assertColumn(jdbcTemplate, "audit_logs", "event_type",
                "varchar(255)", "varchar", true, 255L, null, "");
        assertColumn(jdbcTemplate, "audit_logs", "flag_key",
                "varchar(255)", "varchar", true, 255L, null, "");
        assertColumn(jdbcTemplate, "audit_logs", "timestamp",
                "varchar(255)", "varchar", true, 255L, null, "");

        if (enriched) {
            assertColumn(jdbcTemplate, "audit_logs", "event_id",
                    "varchar(64)", "varchar", true, 64L, null, "");
            assertColumn(jdbcTemplate, "audit_logs", "source_service",
                    "varchar(100)", "varchar", true, 100L, null, "");
            assertColumn(jdbcTemplate, "audit_logs", "actor",
                    "varchar(255)", "varchar", true, 255L, null, "");
            assertColumn(jdbcTemplate, "audit_logs", "before_state",
                    "longtext", "longtext", true, 4_294_967_295L,
                    null, "");
            assertColumn(jdbcTemplate, "audit_logs", "after_state",
                    "longtext", "longtext", true, 4_294_967_295L,
                    null, "");
            assertColumn(jdbcTemplate, "audit_logs", "occurred_at",
                    "datetime(6)", "datetime", true, null, 6L, "");
        }
    }

    private static void assertProcessedEventColumns(
            JdbcTemplate jdbcTemplate
    ) {
        assertEquals(
                List.of("processed_at", "event_id", "topic"),
                columnNames(jdbcTemplate, "processed_kafka_events")
        );

        assertColumn(
                jdbcTemplate,
                "processed_kafka_events",
                "processed_at",
                "datetime(6)",
                "datetime",
                false,
                null,
                6L,
                ""
        );
        assertColumn(
                jdbcTemplate,
                "processed_kafka_events",
                "event_id",
                "varchar(64)",
                "varchar",
                false,
                64L,
                null,
                ""
        );
        assertColumn(
                jdbcTemplate,
                "processed_kafka_events",
                "topic",
                "varchar(120)",
                "varchar",
                false,
                120L,
                null,
                ""
        );
    }

    private static void assertIndexes(
            JdbcTemplate jdbcTemplate,
            boolean enriched
    ) {
        Set<String> expectedIndexes = enriched
                ? Set.of(
                        "audit_logs:PRIMARY",
                        "audit_logs:uk_audit_logs_event_id",
                        "audit_logs:idx_audit_logs_flag_occurred_at",
                        "processed_kafka_events:PRIMARY"
                )
                : Set.of(
                        "audit_logs:PRIMARY",
                        "processed_kafka_events:PRIMARY"
                );

        assertEquals(
                expectedIndexes,
                Set.copyOf(jdbcTemplate.queryForList(
                        """
                        SELECT DISTINCT CONCAT(table_name, ':', index_name)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name IN (
                              'audit_logs',
                              'processed_kafka_events'
                          )
                        """,
                        String.class
                ))
        );

        assertIndex(
                jdbcTemplate,
                "audit_logs",
                "PRIMARY",
                List.of("id"),
                true
        );
        assertIndex(
                jdbcTemplate,
                "processed_kafka_events",
                "PRIMARY",
                List.of("event_id"),
                true
        );

        if (enriched) {
            assertIndex(
                    jdbcTemplate,
                    "audit_logs",
                    "uk_audit_logs_event_id",
                    List.of("event_id"),
                    true
            );
            assertIndex(
                    jdbcTemplate,
                    "audit_logs",
                    "idx_audit_logs_flag_occurred_at",
                    List.of("flag_key", "occurred_at", "id"),
                    false
            );
        }
    }

    private static void assertConstraints(
            JdbcTemplate jdbcTemplate,
            boolean enriched
    ) {
        Set<String> expectedConstraints = enriched
                ? Set.of(
                        "audit_logs:PRIMARY:PRIMARY KEY",
                        "audit_logs:uk_audit_logs_event_id:UNIQUE",
                        "processed_kafka_events:PRIMARY:PRIMARY KEY"
                )
                : Set.of(
                        "audit_logs:PRIMARY:PRIMARY KEY",
                        "processed_kafka_events:PRIMARY:PRIMARY KEY"
                );

        assertEquals(
                expectedConstraints,
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
                              'audit_logs',
                              'processed_kafka_events'
                          )
                        """,
                        String.class
                ))
        );

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name IN (
                              'audit_logs',
                              'processed_kafka_events'
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

    private record TableMetadata(
            String engine,
            String collation,
            String characterSet
    ) {
    }
}
