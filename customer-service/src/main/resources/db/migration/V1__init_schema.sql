CREATE TABLE IF NOT EXISTS customers (
    id               UUID         PRIMARY KEY,
    type             VARCHAR(30)  NOT NULL,
    first_name       VARCHAR(100),
    last_name        VARCHAR(100),
    company_name     VARCHAR(200),
    identity_number  VARCHAR(20)  NOT NULL UNIQUE,
    date_of_birth    DATE,
    status           VARCHAR(30)  NOT NULL,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP,
    deleted_at       TIMESTAMP
);

CREATE TABLE IF NOT EXISTS addresses (
    id              UUID         PRIMARY KEY,
    customer_id     UUID         NOT NULL REFERENCES customers(id),
    line1           VARCHAR(255) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    district        VARCHAR(100) NOT NULL,
    postal_code     VARCHAR(20),
    default_address BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS documents (
    id          UUID         PRIMARY KEY,
    customer_id UUID         NOT NULL REFERENCES customers(id),
    type        VARCHAR(40)  NOT NULL,
    file_ref    VARCHAR(255) NOT NULL,
    verified_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS contact_infos (
    id              UUID         PRIMARY KEY,
    customer_id     UUID         NOT NULL REFERENCES customers(id),
    type            VARCHAR(30)  NOT NULL,
    value           VARCHAR(150) NOT NULL,
    primary_contact BOOLEAN      NOT NULL DEFAULT FALSE
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

CREATE INDEX IF NOT EXISTS idx_outbox_events_status ON outbox_events(status);
CREATE INDEX IF NOT EXISTS idx_customers_identity ON customers(identity_number);
CREATE INDEX IF NOT EXISTS idx_addresses_customer ON addresses(customer_id);
CREATE INDEX IF NOT EXISTS idx_documents_customer ON documents(customer_id);
CREATE INDEX IF NOT EXISTS idx_contact_infos_customer ON contact_infos(customer_id);
