-- Usage Service initial schema
-- Mevcut dev veritabanlarinda tablolar hibernate tarafindan olusturulmus olabileceginden
-- IF NOT EXISTS kullanilir (baseline-on-migrate + baseline-version=0 ile birlikte guvenli).

CREATE SCHEMA IF NOT EXISTS usage_service;

CREATE TABLE IF NOT EXISTS usage_service.usage_records (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL,
    type VARCHAR(10) NOT NULL,
    quantity NUMERIC(12, 4) NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    cdr_ref VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS usage_service.quotas (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    minutes_remaining INT NOT NULL,
    sms_remaining INT NOT NULL,
    mb_remaining INT NOT NULL
);

CREATE TABLE IF NOT EXISTS usage_service.outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS usage_service.processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_usage_records_subscription_id ON usage_service.usage_records (subscription_id);
CREATE INDEX IF NOT EXISTS idx_quotas_subscription_id ON usage_service.quotas (subscription_id);
CREATE INDEX IF NOT EXISTS idx_outbox_events_status ON usage_service.outbox_events (status);
