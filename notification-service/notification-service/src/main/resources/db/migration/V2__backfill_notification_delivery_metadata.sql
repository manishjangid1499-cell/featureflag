UPDATE notifications
SET delivery_mode = 'SYNCHRONOUS'
WHERE delivery_mode IS NULL;

UPDATE notifications
SET attempt_count = 0
WHERE attempt_count IS NULL;
