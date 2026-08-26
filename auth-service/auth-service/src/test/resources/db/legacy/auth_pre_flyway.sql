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

INSERT INTO users (
    id,
    email,
    name,
    password,
    role
) VALUES (
    101,
    'owner@example.test',
    'Fixture Owner',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'OWNER'
);

INSERT INTO invitations (
    id,
    accepted_at,
    created_at,
    expires_at,
    invited_by_user_id,
    token_hash,
    email,
    full_name,
    invited_by_email,
    invited_by_name,
    invited_role,
    status
) VALUES (
    201,
    NULL,
    '2026-08-20 10:00:00.000000',
    '2099-08-20 10:00:00.000000',
    101,
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    'invitee@example.test',
    'Fixture Invitee',
    'owner@example.test',
    'Fixture Owner',
    'DEVELOPER',
    'PENDING'
);
