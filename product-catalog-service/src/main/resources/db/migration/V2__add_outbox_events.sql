CREATE TABLE IF NOT EXISTS product_catalog_service.outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_id   UUID NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT NOT NULL,
    status         VARCHAR(20) NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    published_at   TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_status ON product_catalog_service.outbox_events(status);
