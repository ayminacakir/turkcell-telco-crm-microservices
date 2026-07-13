ALTER TABLE billing_service.bill_cycles
    ADD COLUMN IF NOT EXISTS tariff_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS monthly_fee NUMERIC(12, 2);

CREATE TABLE IF NOT EXISTS billing_service.usage_aggregations (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL,
    customer_id UUID,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    voice_used INT NOT NULL DEFAULT 0,
    sms_used INT NOT NULL DEFAULT 0,
    data_mb_used INT NOT NULL DEFAULT 0,
    overage_voice_minutes INT NOT NULL DEFAULT 0,
    overage_sms INT NOT NULL DEFAULT 0,
    overage_data_mb INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_usage_aggregations_subscription_period
    ON billing_service.usage_aggregations (subscription_id, period_start, period_end);
