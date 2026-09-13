ALTER TABLE payment_metadata ADD COLUMN enriched_at VARCHAR(30);
ALTER TABLE payment_metadata ADD COLUMN velocity_score INT;
ALTER TABLE payment_metadata ADD COLUMN geo_risk_score INT;
