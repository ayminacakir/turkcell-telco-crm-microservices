CREATE TABLE IF NOT EXISTS subscriptions (
    id             UUID        PRIMARY KEY,
    customer_id    UUID        NOT NULL,
    order_id       UUID        NOT NULL,
    msisdn         VARCHAR(20) NOT NULL UNIQUE,
    tariff_code    VARCHAR(100) NOT NULL,
    status         VARCHAR(30) NOT NULL,
    mnp_status     VARCHAR(30) NOT NULL,
    activated_at   TIMESTAMP   NOT NULL,
    terminated_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS msisdn_pool (
    msisdn          VARCHAR(20) PRIMARY KEY,
    status          VARCHAR(20) NOT NULL,
    reserved_until  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sim_cards (
    iccid   VARCHAR(22) PRIMARY KEY,
    imsi    VARCHAR(15) NOT NULL UNIQUE,
    msisdn  VARCHAR(20) NOT NULL,
    status  VARCHAR(20) NOT NULL
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
    event_id     UUID      PRIMARY KEY,
    event_type   VARCHAR   NOT NULL,
    processed_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_customer   ON subscriptions(customer_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_order      ON subscriptions(order_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status     ON subscriptions(status);
CREATE INDEX IF NOT EXISTS idx_msisdn_pool_status       ON msisdn_pool(status);
CREATE INDEX IF NOT EXISTS idx_outbox_status            ON outbox_events(status);
