ALTER TABLE payment_service.payments
    ADD COLUMN IF NOT EXISTS payment_request_id UUID,
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS first_failed_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_payment_request_id
    ON payment_service.payments (payment_request_id)
    WHERE payment_request_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS payment_service.wallets (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL UNIQUE,
    balance NUMERIC(12, 2) NOT NULL DEFAULT 0
);
