-- Billing Service initial schema
-- Mevcut dev veritabanlarinda tablolar hibernate tarafindan olusturulmus olabileceginden
-- IF NOT EXISTS kullanilir (baseline-on-migrate + baseline-version=0 ile birlikte guvenli).

CREATE SCHEMA IF NOT EXISTS billing_service;

CREATE TABLE IF NOT EXISTS billing_service.bill_cycles (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    subscription_id UUID,
    day_of_month INT NOT NULL,
    next_run_date DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS billing_service.invoices (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    sub_total NUMERIC(12, 2) NOT NULL,
    tax NUMERIC(12, 2) NOT NULL,
    grand_total NUMERIC(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    due_date DATE NOT NULL,
    issued_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS billing_service.invoice_lines (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES billing_service.invoices (id),
    description VARCHAR(255) NOT NULL,
    quantity NUMERIC(10, 4) NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL,
    line_total NUMERIC(12, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS billing_service.outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_service.processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bill_cycles_customer_id ON billing_service.bill_cycles (customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_customer_id ON billing_service.invoices (customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_subscription_id ON billing_service.invoices (subscription_id);
CREATE INDEX IF NOT EXISTS idx_invoice_lines_invoice_id ON billing_service.invoice_lines (invoice_id);
CREATE INDEX IF NOT EXISTS idx_outbox_events_status ON billing_service.outbox_events (status);
