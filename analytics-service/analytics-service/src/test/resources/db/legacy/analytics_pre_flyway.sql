CREATE TABLE analytics_events (
    count BIGINT DEFAULT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    environment VARCHAR(255) DEFAULT NULL,
    event_type VARCHAR(255) DEFAULT NULL,
    flag_key VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processed_kafka_events (
    processed_at DATETIME(6) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    topic VARCHAR(120) NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO analytics_events (
    count,
    id,
    environment,
    event_type,
    flag_key
) VALUES
    (
        5,
        101,
        'DEV',
        'FLAG_EVALUATED',
        'fixture-checkout'
    ),
    (
        3,
        102,
        'DEV',
        'FLAG_EVALUATED',
        'fixture-checkout'
    ),
    (
        NULL,
        103,
        NULL,
        NULL,
        NULL
    );

INSERT INTO processed_kafka_events (
    processed_at,
    event_id,
    topic
) VALUES (
    '2026-08-20 10:01:02.123456',
    'legacy-analytics-event-001',
    'feature-flag-events'
);
