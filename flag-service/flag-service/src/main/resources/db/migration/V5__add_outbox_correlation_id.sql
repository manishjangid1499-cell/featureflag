ALTER TABLE outbox_events
    ADD COLUMN correlation_id VARCHAR(64) NULL AFTER message_key;
