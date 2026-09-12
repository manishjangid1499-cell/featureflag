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
