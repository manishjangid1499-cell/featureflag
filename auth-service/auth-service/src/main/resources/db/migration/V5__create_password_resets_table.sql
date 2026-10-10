CREATE TABLE password_resets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    credential_version BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    status ENUM('PENDING', 'USED', 'EXPIRED', 'REVOKED') NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_password_resets_token_hash UNIQUE (token_hash),
    INDEX idx_password_resets_user_id_status (user_id, status),
    INDEX idx_password_resets_email_status (email, status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
