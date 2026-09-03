CREATE INDEX idx_outbox_status_published_at_id
    ON outbox_events (status, published_at, id);
