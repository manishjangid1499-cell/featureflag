package com.featureflag.notification_service.migration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NotificationMigrationSchemaAssertions {

    static final String DUE_INDEX =
            "idx_notifications_delivery_due";
    static final String LEASE_INDEX =
            "idx_notifications_delivery_lease";
    static final String DELIVERY_MODE_CHECK =
            "notifications_chk_1";

    private NotificationMigrationSchemaAssertions() {
    }

    static void assertPreFlywayLegacySchema(JdbcTemplate jdbcTemplate) {
        assertFalse(tableExists(jdbcTemplate, "flyway_schema_history"));
        assertBaseSchema(jdbcTemplate);
        assertFalse(indexExists(jdbcTemplate, DUE_INDEX));
        assertFalse(indexExists(jdbcTemplate, LEASE_INDEX));
        assertFalse(indexExists(
                jdbcTemplate,
                "idx_test_notifications_delivery_due"
        ));
    }

    static void assertMigratedSchema(JdbcTemplate jdbcTemplate) {
        assertTrue(tableExists(jdbcTemplate, "flyway_schema_history"));
        assertBaseSchema(jdbcTemplate);
        assertIndexColumns(
                jdbcTemplate,
                DUE_INDEX,
                List.of(
                        "delivery_mode",
                        "next_attempt_at",
                        "id",
                        "status"
                )
        );
        assertIndexColumns(
                jdbcTemplate,
                LEASE_INDEX,
                List.of(
                        "delivery_mode",
                        "status",
                        "lease_until",
                        "id"
                )
        );
        assertFalse(indexExists(
                jdbcTemplate,
                "idx_test_notifications_delivery_due"
        ));
    }

    static String deliveryModeCheckClause(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject(
                """
                SELECT check_clause
                FROM information_schema.check_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name = ?
                """,
                String.class,
                DELIVERY_MODE_CHECK
        );
    }

    private static void assertBaseSchema(JdbcTemplate jdbcTemplate) {
        assertEquals(
                Set.of(
                        "notifications",
                        "processed_kafka_events"
                ),
                Set.copyOf(jdbcTemplate.queryForList(
                        """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name IN (
                              'notifications',
                              'processed_kafka_events'
                          )
                        """,
                        String.class
                ))
        );

        assertTable(jdbcTemplate, "notifications");
        assertTable(jdbcTemplate, "processed_kafka_events");
        assertNotificationColumns(jdbcTemplate);
        assertProcessedEventColumns(jdbcTemplate);
        assertIndexColumns(
                jdbcTemplate,
                "PRIMARY",
                "notifications",
                List.of("id")
        );
        assertIndexColumns(
                jdbcTemplate,
                "PRIMARY",
                "processed_kafka_events",
                List.of("event_id")
        );
        assertDeliveryModeCheck(jdbcTemplate);
    }

    private static void assertNotificationColumns(
            JdbcTemplate jdbcTemplate
    ) {
        assertEquals(
                List.of(
                        "attempt_count",
                        "created_at",
                        "id",
                        "last_attempt_at",
                        "lease_until",
                        "next_attempt_at",
                        "sent_at",
                        "delivery_mode",
                        "claim_token",
                        "last_error_type",
                        "creator_email",
                        "message",
                        "recipient",
                        "status",
                        "subject",
                        "type"
                ),
                columnNames(jdbcTemplate, "notifications")
        );

        assertColumn(jdbcTemplate, "notifications", "attempt_count",
                "int", true, null, null, "");
        assertColumn(jdbcTemplate, "notifications", "created_at",
                "datetime", false, null, 6L, "");
        assertColumn(jdbcTemplate, "notifications", "id",
                "bigint", false, null, null, "auto_increment");
        assertColumn(jdbcTemplate, "notifications", "last_attempt_at",
                "datetime", true, null, 6L, "");
        assertColumn(jdbcTemplate, "notifications", "lease_until",
                "datetime", true, null, 6L, "");
        assertColumn(jdbcTemplate, "notifications", "next_attempt_at",
                "datetime", true, null, 6L, "");
        assertColumn(jdbcTemplate, "notifications", "sent_at",
                "datetime", true, null, 6L, "");
        assertColumn(jdbcTemplate, "notifications", "delivery_mode",
                "varchar", true, 20L, null, "");
        assertColumn(jdbcTemplate, "notifications", "claim_token",
                "varchar", true, 36L, null, "");
        assertColumn(jdbcTemplate, "notifications", "last_error_type",
                "varchar", true, 128L, null, "");
        assertColumn(jdbcTemplate, "notifications", "creator_email",
                "varchar", true, 255L, null, "");
        assertColumn(jdbcTemplate, "notifications", "message",
                "text", false, 65_535L, null, "");
        assertColumn(jdbcTemplate, "notifications", "recipient",
                "varchar", false, 255L, null, "");
        assertColumn(jdbcTemplate, "notifications", "status",
                "varchar", false, 255L, null, "");
        assertColumn(jdbcTemplate, "notifications", "subject",
                "varchar", false, 255L, null, "");
        assertColumn(jdbcTemplate, "notifications", "type",
                "varchar", false, 255L, null, "");
    }

    private static void assertProcessedEventColumns(
            JdbcTemplate jdbcTemplate
    ) {
        assertEquals(
                List.of(
                        "processed_at",
                        "event_id",
                        "topic"
                ),
                columnNames(
                        jdbcTemplate,
                        "processed_kafka_events"
                )
        );
        assertColumn(
                jdbcTemplate,
                "processed_kafka_events",
                "processed_at",
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
                "varchar",
                false,
                120L,
                null,
                ""
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
        assertEquals(
                "utf8mb4_0900_ai_ci",
                metadata.collation()
        );
    }

    private static void assertColumn(
            JdbcTemplate jdbcTemplate,
            String tableName,
            String columnName,
            String dataType,
            boolean nullable,
            Long length,
            Long datetimePrecision,
            String extra
    ) {
        ColumnMetadata metadata = jdbcTemplate.queryForObject(
                """
                SELECT data_type,
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
                        resultSet.getString("data_type"),
                        "YES".equals(resultSet.getString("is_nullable")),
                        nullableLong(
                                resultSet.getObject(
                                        "character_maximum_length"
                                )
                        ),
                        nullableLong(
                                resultSet.getObject("datetime_precision")
                        ),
                        resultSet.getString("extra")
                ),
                tableName,
                columnName
        );
        assertNotNull(metadata);
        assertEquals(dataType, metadata.dataType());
        assertEquals(nullable, metadata.nullable());
        assertEquals(length, metadata.length());
        assertEquals(datetimePrecision, metadata.datetimePrecision());
        assertEquals(extra, metadata.extra());
    }

    private static void assertDeliveryModeCheck(
            JdbcTemplate jdbcTemplate
    ) {
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints
                        WHERE constraint_schema = DATABASE()
                          AND table_name = 'notifications'
                          AND constraint_name = ?
                          AND constraint_type = 'CHECK'
                        """,
                        Integer.class,
                        DELIVERY_MODE_CHECK
                )
        );
        String clause = deliveryModeCheckClause(jdbcTemplate);
        assertNotNull(clause);
        String normalized = clause
                .toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replace("_utf8mb4", "")
                .replaceAll("\\s+", "");
        assertTrue(normalized.contains("delivery_mode"));
        assertTrue(normalized.contains("synchronous"));
        assertTrue(normalized.contains("durable"));
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

    private static void assertIndexColumns(
            JdbcTemplate jdbcTemplate,
            String indexName,
            List<String> expectedColumns
    ) {
        assertIndexColumns(
                jdbcTemplate,
                indexName,
                "notifications",
                expectedColumns
        );
    }

    private static void assertIndexColumns(
            JdbcTemplate jdbcTemplate,
            String indexName,
            String tableName,
            List<String> expectedColumns
    ) {
        assertEquals(
                expectedColumns,
                jdbcTemplate.queryForList(
                        """
                        SELECT column_name
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                          AND index_name = ?
                        ORDER BY seq_in_index
                        """,
                        String.class,
                        tableName,
                        indexName
                )
        );
    }

    private static boolean indexExists(
            JdbcTemplate jdbcTemplate,
            String indexName
    ) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) > 0
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND index_name = ?
                """,
                Boolean.class,
                indexName
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
            String dataType,
            boolean nullable,
            Long length,
            Long datetimePrecision,
            String extra
    ) {
    }

    private record TableMetadata(
            String engine,
            String collation
    ) {
    }
}
