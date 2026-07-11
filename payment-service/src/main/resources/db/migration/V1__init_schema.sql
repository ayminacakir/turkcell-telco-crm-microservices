-- Payment Service initial schema
-- Mevcut dev veritabanlarinda tablolar hibernate tarafindan olusturulmus olabileceginden
-- IF NOT EXISTS kullanilir (baseline-on-migrate + baseline-version=0 ile birlikte guvenli).

CREATE SCHEMA IF NOT EXISTS payment_service;

CREATE TABLE IF NOT EXISTS payment_service.payments (
    id UUID PRIMARY KEY,
    invoice_id UUID,
    order_id UUID,
    customer_id UUID,
    amount NUMERIC(12, 2) NOT NULL,
    method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    external_ref VARCHAR(200),
    paid_at TIMESTAMP
);

-- Siparis odemelerinde fatura bulunmadigindan invoice_id nullable olmalidir.
-- Eski dev veritabanlarinda kolon NOT NULL olusturulmus olabilir.
ALTER TABLE payment_service.payments ALTER COLUMN invoice_id DROP NOT NULL;

CREATE TABLE IF NOT EXISTS payment_service.payment_attempts (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment_service.payments (id),
    attempt_no INT NOT NULL,
    response TEXT,
    attempted_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS payment_service.audit_log (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID,
    action VARCHAR(50) NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS payment_service.outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS payment_service.processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payment_service.payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_invoice_id ON payment_service.payments (invoice_id);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_payment_id ON payment_service.payment_attempts (payment_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity_id ON payment_service.audit_log (entity_id);
CREATE INDEX IF NOT EXISTS idx_outbox_events_status ON payment_service.outbox_events (status);
