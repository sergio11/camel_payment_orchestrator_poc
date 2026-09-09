CREATE TABLE IF NOT EXISTS payment_metadata (
    payment_id UUID PRIMARY KEY REFERENCES payments(id) ON DELETE CASCADE,
    order_id VARCHAR(100),
    attempts INT,
    is_new_payment_method BOOLEAN,
    payment_method_age_days INT,
    customer_risk_tier VARCHAR(20),
    additional_properties VARCHAR(2048)
);
