-- Flyway V1 calismadan once eski Hibernate tablolarindaki eksik kolonlari tamamlar.
-- V1 degistirilmeden legacy dev DB'lerde index hatasini onler.

CREATE SCHEMA IF NOT EXISTS payment_service;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'payment_service'
          AND table_name = 'payments'
    ) THEN
        ALTER TABLE payment_service.payments ADD COLUMN IF NOT EXISTS order_id UUID;
        ALTER TABLE payment_service.payments ADD COLUMN IF NOT EXISTS customer_id UUID;
        ALTER TABLE payment_service.payments ADD COLUMN IF NOT EXISTS external_ref VARCHAR(200);
        ALTER TABLE payment_service.payments ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP;
    END IF;
END $$;
