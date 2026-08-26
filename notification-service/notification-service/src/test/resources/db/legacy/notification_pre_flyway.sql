CREATE TABLE notifications (
    attempt_count INT DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    last_attempt_at DATETIME(6) DEFAULT NULL,
    lease_until DATETIME(6) DEFAULT NULL,
    next_attempt_at DATETIME(6) DEFAULT NULL,
    sent_at DATETIME(6) DEFAULT NULL,
    delivery_mode VARCHAR(20) DEFAULT NULL,
    claim_token VARCHAR(36) DEFAULT NULL,
    last_error_type VARCHAR(128) DEFAULT NULL,
    creator_email VARCHAR(255) DEFAULT NULL,
    message TEXT NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT notifications_chk_1
        CHECK (delivery_mode IN ('SYNCHRONOUS', 'DURABLE'))
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

INSERT INTO notifications (
    id,
    attempt_count,
    created_at,
    delivery_mode,
    message,
    next_attempt_at,
    recipient,
    sent_at,
    status,
    subject,
    type
) VALUES
    (
        101,
        NULL,
        '2026-08-20 10:00:00.000000',
        NULL,
        'Historical message',
        NULL,
        'historical@example.test',
        '2026-08-20 10:01:00.000000',
        'SENT',
        'Historical subject',
        'EMAIL'
    ),
    (
        102,
        0,
        '2026-08-25 10:00:00.000000',
        'DURABLE',
        'Durable message',
        '2026-08-26 11:00:00.000000',
        'durable@example.test',
        NULL,
        'PENDING',
        'Durable subject',
        'EMAIL'
    ),
    (
        103,
        0,
        '2026-08-21 10:00:00.000000',
        'SYNCHRONOUS',
        'Historical unsupported channel',
        NULL,
        'unsupported@example.test',
        NULL,
        'FAILED',
        'Historical unsupported subject',
        'SMS'
    );

INSERT INTO processed_kafka_events (
    event_id,
    processed_at,
    topic
) VALUES (
    'legacy-event-001',
    '2026-08-25 10:00:00.000000',
    'notification-events'
);
