CREATE TABLE IF NOT EXISTS orders (
    id           UUID           PRIMARY KEY,
    customer_id  UUID           NOT NULL,
    status       VARCHAR(40)    NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency     VARCHAR(3)     NOT NULL DEFAULT 'TRY',
    created_at   TIMESTAMP      NOT NULL,
    updated_at   TIMESTAMP,
    cancelled_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_items (
    id               UUID           PRIMARY KEY,
    order_id         UUID           NOT NULL REFERENCES orders(id),
    product_id       UUID           NOT NULL,
    product_name     VARCHAR(150),
    product_code     VARCHAR(100),
    product_type     VARCHAR(20),
    quantity         INTEGER        NOT NULL,
    unit_price       NUMERIC(12, 2),
    line_total       NUMERIC(12, 2),
    minutes_included INTEGER,
    sms_included     INTEGER,
    data_mb_included INTEGER
);

CREATE TABLE IF NOT EXISTS saga_states (
    id         UUID        PRIMARY KEY,
    order_id   UUID        NOT NULL,
    status     VARCHAR(50) NOT NULL,
    last_error VARCHAR(500),
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id             UUID      PRIMARY KEY,
    aggregate_id   UUID      NOT NULL,
    aggregate_type VARCHAR   NOT NULL,
    event_type     VARCHAR   NOT NULL,
    payload        TEXT      NOT NULL,
    status         VARCHAR   NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    published_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id     UUID        PRIMARY KEY,
    event_type   VARCHAR     NOT NULL,
    processed_at TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_orders_customer   ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status     ON orders(status);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_saga_order        ON saga_states(order_id);
CREATE INDEX IF NOT EXISTS idx_outbox_status     ON outbox_events(status);
