-- Yeni siparis sozlesmesi urunu 'productCode' ile referanslar; 'product_id' opsiyoneldir
-- (CreateOrderItemRequest.productId yeni akista null olabilir). V1'deki NOT NULL kisiti
-- bu akisi patlatiyordu (order_items.product_id null -> DataIntegrityViolation).
ALTER TABLE order_items ALTER COLUMN product_id DROP NOT NULL;
