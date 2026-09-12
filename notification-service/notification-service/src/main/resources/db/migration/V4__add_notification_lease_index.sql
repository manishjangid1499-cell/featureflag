CREATE INDEX idx_notifications_delivery_lease
ON notifications (
    delivery_mode,
    status,
    lease_until,
    id
);
