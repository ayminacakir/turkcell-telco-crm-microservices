-- Eski dev/Hibernate veritabanlarinda payments tablosu V1 oncesi eksik kolonlarla
-- olusturulmus olabilir. V1 CREATE TABLE IF NOT EXISTS bu kolonlari eklemez;
-- idx_payments_order_id index'i order_id olmadan patlar.

ALTER TABLE payment_service.payments ADD COLUMN IF NOT EXISTS order_id UUID;
ALTER TABLE payment_service.payments ADD COLUMN IF NOT EXISTS customer_id UUID;
ALTER TABLE payment_service.payments ADD COLUMN IF NOT EXISTS external_ref VARCHAR(200);
ALTER TABLE payment_service.payments ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payment_service.payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_invoice_id ON payment_service.payments (invoice_id);
