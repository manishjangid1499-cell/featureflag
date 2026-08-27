ALTER TABLE audit_logs
    ADD COLUMN event_id VARCHAR(64) NULL,
    ADD COLUMN source_service VARCHAR(100) NULL,
    ADD COLUMN actor VARCHAR(255) NULL,
    ADD COLUMN before_state LONGTEXT NULL,
    ADD COLUMN after_state LONGTEXT NULL,
    ADD COLUMN occurred_at DATETIME(6) NULL,
    ADD CONSTRAINT uk_audit_logs_event_id UNIQUE (event_id),
    ADD INDEX idx_audit_logs_flag_occurred_at (
        flag_key,
        occurred_at,
        id
    );
