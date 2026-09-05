CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    customerId VARCHAR(50) NOT NULL,
    paymentMethod VARCHAR(30),
    country VARCHAR(2),
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(100),
    failureReason VARCHAR(1024),
    metadataJson TEXT,
    createdAt TIMESTAMP NOT NULL,
    updatedAt TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_payments_customer ON payments (customerId);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments (status);

CREATE TABLE IF NOT EXISTS outbox_event (
    id UUID PRIMARY KEY,
    aggregateId UUID NOT NULL,
    type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    createdAt TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_event (status);
