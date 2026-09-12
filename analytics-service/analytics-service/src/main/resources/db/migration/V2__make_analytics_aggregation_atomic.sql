UPDATE analytics_events
SET count = 0
WHERE count IS NULL;

CREATE TEMPORARY TABLE analytics_event_duplicate_rollups
ENGINE=InnoDB
AS
SELECT MIN(id) AS retained_id,
       flag_key,
       environment,
       event_type,
       SUM(count) AS total_count
FROM analytics_events
WHERE flag_key IS NOT NULL
  AND environment IS NOT NULL
  AND event_type IS NOT NULL
GROUP BY flag_key, environment, event_type
HAVING COUNT(*) > 1;

UPDATE analytics_events AS retained
JOIN analytics_event_duplicate_rollups AS rollup
  ON retained.id = rollup.retained_id
SET retained.count = rollup.total_count;

DELETE duplicate_row
FROM analytics_events AS duplicate_row
JOIN analytics_event_duplicate_rollups AS rollup
  ON duplicate_row.flag_key = rollup.flag_key
 AND duplicate_row.environment = rollup.environment
 AND duplicate_row.event_type = rollup.event_type
WHERE duplicate_row.id <> rollup.retained_id;

DROP TEMPORARY TABLE analytics_event_duplicate_rollups;

ALTER TABLE analytics_events
    MODIFY COLUMN count BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uk_analytics_events_dimensions
        UNIQUE (flag_key, environment, event_type);
