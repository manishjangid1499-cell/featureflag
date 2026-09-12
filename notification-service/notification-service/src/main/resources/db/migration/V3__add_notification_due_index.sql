CREATE INDEX idx_notifications_delivery_due
ON notifications (
    delivery_mode,
    next_attempt_at,
    id,
    status
);
