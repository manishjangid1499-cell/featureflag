package com.featureflag.analytics_service.migration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnalyticsMigrationSchemaAssertions {

    private static final Set<String> BUSINESS_TABLES = Set.of(
            "analytics_events",
            "processed_kafka_events"
    );

    private AnalyticsMigrationSchemaAssertions() {
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
            boolean migrated
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
        assertAnalyticsEventColumns(jdbcTemplate, migrated);
        assertProcessedEventColumns(jdbcTemplate);
        assertIndexes(jdbcTemplate, migrated);
        assertConstraints(jdbcTemplate, migrated);
    }

    private static void assertAnalyticsEventColumns(
            JdbcTemplate jdbcTemplate,
            boolean migrated
    ) {
        assertEquals(
                List.of(
                        "count",
                        "id",
                        "environment",
                        "event_type",
                        "flag_key"
                ),
                columnNames(jdbcTemplate, "analytics_events")
        );

        assertColumn(
                jdbcTemplate,
                "analytics_events",
                "count",
                "bigint",
                "bigint",
                !migrated,
                null,
                null,
                "",
                migrated ? "0" : null
        );
        assertColumn(jdbcTemplate, "analytics_events", "id",
                "bigint", "bigint", false, null, null,
                "auto_increment");
        assertColumn(jdbcTemplate, "analytics_events", "environment",
                "varchar(255)", "varchar", true, 255L, null, "");
        assertColumn(jdbcTemplate, "analytics_events", "event_type",
                "varchar(255)", "varchar", true, 255L, null, "");
        assertColumn(jdbcTemplate, "analytics_events", "flag_key",
                "varchar(255)", "varchar", true, 255L, null, "");
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
            boolean migrated
    ) {
        assertEquals(
                migrated
                        ? Set.of(
                                "analytics_events:PRIMARY",
                                "analytics_events:"
                                        + "uk_analytics_events_dimensions",
                                "processed_kafka_events:PRIMARY"
                        )
                        : Set.of(
                                "analytics_events:PRIMARY",
                                "processed_kafka_events:PRIMARY"
                        ),
                Set.copyOf(jdbcTemplate.queryForList(
                        """
                        SELECT DISTINCT CONCAT(table_name, ':', index_name)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name IN (
                              'analytics_events',
                              'processed_kafka_events'
                          )
                        """,
                        String.class
                ))
        );

        assertIndex(
                jdbcTemplate,
                "analytics_events",
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
        if (migrated) {
            assertIndex(
                    jdbcTemplate,
                    "analytics_events",
                    "uk_analytics_events_dimensions",
                    List.of(
                            "flag_key",
                            "environment",
                            "event_type"
                    ),
                    true
            );
        } else {
            assertNoAggregateUniqueIndex(jdbcTemplate);
        }
    }

    private static void assertConstraints(
            JdbcTemplate jdbcTemplate,
            boolean migrated
    ) {
        assertEquals(
                migrated
                        ? Set.of(
                                "analytics_events:PRIMARY:PRIMARY KEY",
                                "analytics_events:"
                                        + "uk_analytics_events_dimensions:"
                                        + "UNIQUE",
                                "processed_kafka_events:PRIMARY:PRIMARY KEY"
                        )
                        : Set.of(
                                "analytics_events:PRIMARY:PRIMARY KEY",
                                "processed_kafka_events:PRIMARY:PRIMARY KEY"
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
                              'analytics_events',
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
                              'analytics_events',
                              'processed_kafka_events'
                          )
                          AND data_type = 'enum'
                        """,
                        Integer.class
                )
        );
    }

    private static void assertNoAggregateUniqueIndex(
            JdbcTemplate jdbcTemplate
    ) {
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(DISTINCT index_name)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'analytics_events'
                          AND non_unique = 0
                          AND index_name <> 'PRIMARY'
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
        assertColumn(
                jdbcTemplate,
                tableName,
                columnName,
                columnType,
                dataType,
                nullable,
                length,
                datetimePrecision,
                extra,
                null
        );
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
            String extra,
            String defaultValue
    ) {
        ColumnMetadata metadata = jdbcTemplate.queryForObject(
                """
                SELECT column_type,
                       data_type,
                       is_nullable,
                       character_maximum_length,
                       datetime_precision,
                       extra,
                       column_default
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
                        resultSet.getString("extra"),
                        resultSet.getString("column_default")
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
        assertEquals(defaultValue, metadata.defaultValue());
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
            String extra,
            String defaultValue
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
