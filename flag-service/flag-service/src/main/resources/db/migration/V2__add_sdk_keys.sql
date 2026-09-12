CREATE TABLE sdk_keys (
    active BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    revoked_at DATETIME(6) DEFAULT NULL,
    environment VARCHAR(20) NOT NULL,
    key_prefix VARCHAR(20) NOT NULL,
    name VARCHAR(120) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sdk_keys_key_hash UNIQUE (key_hash)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
