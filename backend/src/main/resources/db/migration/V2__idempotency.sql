ALTER TABLE payments ADD COLUMN IF NOT EXISTS idempotencyKey VARCHAR(36);
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_idempotency ON payments (idempotencyKey);

ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS idempotencyKey VARCHAR(36);
CREATE UNIQUE INDEX IF NOT EXISTS uq_outbox_idempotency ON outbox_event (idempotencyKey);
