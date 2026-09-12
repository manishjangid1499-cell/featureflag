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

INSERT INTO feature_flags (
    id,
    enabled,
    rollout_percentage,
    end_date,
    start_date,
    description,
    environment,
    flag_key,
    name
) VALUES (
    101,
    b'1',
    50,
    '2099-08-20 10:00:00.000000',
    '2026-08-20 10:00:00.000000',
    'Fixture checkout flag',
    'DEV',
    'fixture-checkout',
    'Fixture Checkout'
);

INSERT INTO flag_target_users (flag_id, user_id) VALUES
    (101, 'fixture-user-a'),
    (101, 'fixture-user-b');

INSERT INTO outbox_events (
    attempts,
    created_at,
    next_attempt_at,
    published_at,
    status,
    id,
    event_type,
    topic,
    last_error_type,
    message_key,
    payload
) VALUES
    (
        2,
        '2026-08-20 11:00:00.000000',
        '2099-08-20 11:00:00.000000',
        NULL,
        'PENDING',
        '11111111-1111-1111-1111-111111111111',
        'FLAG_UPDATED',
        'feature-flag-events',
        'ExecutionException',
        'fixture-checkout',
        '{"eventId":"11111111-1111-1111-1111-111111111111","state":"pending"}'
    ),
    (
        1,
        '2026-08-20 12:00:00.000000',
        '2026-08-20 12:00:00.000000',
        '2026-08-20 12:01:00.000000',
        'PUBLISHED',
        '22222222-2222-2222-2222-222222222222',
        'FLAG_CREATED',
        'feature-flag-events',
        NULL,
        'fixture-checkout',
        '{"eventId":"22222222-2222-2222-2222-222222222222","state":"published"}'
    ),
    (
        10,
        '2026-08-20 13:00:00.000000',
        '2026-08-20 13:01:00.000000',
        NULL,
        'DEAD',
        '33333333-3333-3333-3333-333333333333',
        'NOTIFICATION',
        'notification-events',
        'ExecutionException',
        '33333333-3333-3333-3333-333333333333',
        '{"eventId":"33333333-3333-3333-3333-333333333333","state":"dead"}'
    );
