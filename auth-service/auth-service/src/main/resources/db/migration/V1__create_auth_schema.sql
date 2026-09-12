CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255) DEFAULT NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM(
        'ADMIN',
        'DEVELOPER',
        'OWNER',
        'VIEWER'
    ) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UK6dotkott2kjsp8vw4d0m25fb7
        UNIQUE (email)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invitations (
    accepted_at DATETIME(6) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    invited_by_user_id BIGINT DEFAULT NULL,
    token_hash VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) DEFAULT NULL,
    invited_by_email VARCHAR(255) DEFAULT NULL,
    invited_by_name VARCHAR(255) DEFAULT NULL,
    invited_role ENUM(
        'ADMIN',
        'DEVELOPER',
        'OWNER',
        'VIEWER'
    ) NOT NULL,
    status ENUM(
        'ACCEPTED',
        'EXPIRED',
        'PENDING',
        'REVOKED'
    ) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UK6a03cl7cgxqwekvwbi3dmdruk
        UNIQUE (token_hash)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
