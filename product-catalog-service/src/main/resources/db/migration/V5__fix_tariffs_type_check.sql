-- Lokal dev DB'lerde tariffs tablosu ilk basta Hibernate ddl-auto tarafindan
-- olusturulmus olabilir; Hibernate enum kolonlari icin CHECK constraint uretir
-- (type IN ('POSTPAID','PREPAID')) ve ddl-auto:update bunu asla guncellemez.
-- Enum'a HYBRID eklendigi icin constraint yeniden olusturulur.
-- (Ayni kalip: notification-service V6__fix_notifications_status_check.sql)
ALTER TABLE product_catalog_service.tariffs
    DROP CONSTRAINT IF EXISTS tariffs_type_check;

ALTER TABLE product_catalog_service.tariffs
    ADD CONSTRAINT tariffs_type_check
    CHECK (type IN ('POSTPAID', 'PREPAID', 'HYBRID'));
