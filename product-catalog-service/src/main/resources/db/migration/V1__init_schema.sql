CREATE SCHEMA IF NOT EXISTS product_catalog_service;

CREATE TABLE IF NOT EXISTS product_catalog_service.tariffs (
    id                UUID PRIMARY KEY,
    code              VARCHAR(50) NOT NULL UNIQUE,
    name              VARCHAR(255) NOT NULL,
    type              VARCHAR(20) NOT NULL,
    monthly_fee       NUMERIC(10,2) NOT NULL,
    minutes_included  INTEGER,
    sms_included      INTEGER,
    data_mb_included  INTEGER,
    status            VARCHAR(20) NOT NULL,
    effective_from    DATE,
    effective_to      DATE
);

CREATE TABLE IF NOT EXISTS product_catalog_service.addons (
    id             UUID PRIMARY KEY,
    code           VARCHAR(50) NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    price          NUMERIC(10,2) NOT NULL,
    type           VARCHAR(20) NOT NULL,
    validity_days  INTEGER
);

CREATE TABLE IF NOT EXISTS product_catalog_service.tariff_addons (
    tariff_id  UUID NOT NULL REFERENCES product_catalog_service.tariffs(id),
    addon_id   UUID NOT NULL REFERENCES product_catalog_service.addons(id),
    PRIMARY KEY (tariff_id, addon_id)
);
