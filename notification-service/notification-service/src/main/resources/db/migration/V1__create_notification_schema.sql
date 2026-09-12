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
