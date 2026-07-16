-- FR-06: her urunun hedef segmenti vardir.
-- FR-08: tarife degisiklikleri versiyonlanmali, eski abonelerin tarifesi korunmalidir.
ALTER TABLE product_catalog_service.tariffs
    ADD COLUMN IF NOT EXISTS target_segment VARCHAR(50);

ALTER TABLE product_catalog_service.tariffs
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- Her degisiklikte tarifenin ONCEKI hali buraya snapshot olarak yazilir.
-- Boylece eski abonelerin bagli oldugu tarife kosullari sorgulanabilir kalir.
CREATE TABLE IF NOT EXISTS product_catalog_service.tariff_versions (
    id                UUID PRIMARY KEY,
    tariff_id         UUID NOT NULL REFERENCES product_catalog_service.tariffs(id),
    version           INTEGER NOT NULL,
    code              VARCHAR(50) NOT NULL,
    name              VARCHAR(255) NOT NULL,
    type              VARCHAR(20) NOT NULL,
    monthly_fee       NUMERIC(10,2) NOT NULL,
    minutes_included  INTEGER,
    sms_included      INTEGER,
    data_mb_included  INTEGER,
    status            VARCHAR(20) NOT NULL,
    effective_from    DATE,
    effective_to      DATE,
    target_segment    VARCHAR(50),
    archived_at       TIMESTAMP NOT NULL,
    CONSTRAINT uq_tariff_versions_tariff_version UNIQUE (tariff_id, version)
);

CREATE INDEX IF NOT EXISTS idx_tariff_versions_code ON product_catalog_service.tariff_versions(code);
