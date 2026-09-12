CREATE TABLE feature_flags (
    enabled BIT(1) DEFAULT NULL,
    rollout_percentage INT DEFAULT NULL,
    end_date DATETIME(6) DEFAULT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    start_date DATETIME(6) DEFAULT NULL,
    description VARCHAR(255) DEFAULT NULL,
    environment VARCHAR(255) NOT NULL,
    flag_key VARCHAR(255) NOT NULL,
    name VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_flag_key_environment
        UNIQUE (flag_key, environment)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE flag_target_users (
    flag_id BIGINT NOT NULL,
    user_id VARCHAR(255) DEFAULT NULL,
    KEY FKa9lsvqrrunroyjdceekdqrj6l (flag_id),
    CONSTRAINT FKa9lsvqrrunroyjdceekdqrj6l
        FOREIGN KEY (flag_id)
        REFERENCES feature_flags (id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE outbox_events (
    attempts INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) DEFAULT NULL,
    status VARCHAR(20) NOT NULL,
    id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(120) NOT NULL,
    last_error_type VARCHAR(255) DEFAULT NULL,
    message_key VARCHAR(255) DEFAULT NULL,
    payload LONGTEXT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_outbox_status_next_attempt (
        status,
        next_attempt_at
    ),
    KEY idx_outbox_created_at (created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
