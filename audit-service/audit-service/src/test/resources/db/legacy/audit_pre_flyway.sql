CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    environment VARCHAR(255) DEFAULT NULL,
    event_type VARCHAR(255) DEFAULT NULL,
    flag_key VARCHAR(255) DEFAULT NULL,
    timestamp VARCHAR(255) DEFAULT NULL,
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

INSERT INTO audit_logs (
    id,
    environment,
    event_type,
    flag_key,
    timestamp
) VALUES
    (
        101,
        'DEV',
        'FLAG_UPDATED',
        'fixture-checkout',
        '2026-08-20T10:00:00.123456Z'
    ),
    (
        102,
        NULL,
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
    'legacy-audit-event-001',
    'feature-flag-events'
);
