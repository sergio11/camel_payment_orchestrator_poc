CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_event (aggregateId);
