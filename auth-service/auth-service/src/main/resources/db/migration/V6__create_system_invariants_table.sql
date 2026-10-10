CREATE TABLE system_invariants (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    owner_user_id BIGINT DEFAULT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_system_invariants_owner FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO system_invariants (name, owner_user_id, updated_at)
VALUES ('OWNER_INSTALLATION', (SELECT id FROM users WHERE role = 'OWNER' LIMIT 1), NOW(6));
