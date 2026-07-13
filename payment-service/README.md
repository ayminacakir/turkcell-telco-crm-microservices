# Payment Service

Sipariş ve fatura ödemelerini yöneten servis. Mock payment gateway / wallet ile ödeme dener,
sonucu **Kafka Outbox Pattern** ile yayınlar, başarısız fatura ödemelerinde retry planlar
ve tüm ödeme işlemlerini `audit_log` tablosuna kaydeder.

## Port / Veritabanı

| | |
|---|---|
| Port | 9008 |
| DB | `payment_db` (schema: `payment_service`) |
| Migration | Flyway — `V1__init_schema.sql`, `V2__add_retry_wallet_idempotency.sql` |

## Konfigürasyon

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka adresi |
| `spring.flyway.enabled` | `true` | Flyway migration |
| `payment.gateway.success-rate` | `0.8` | Mock gateway başarı oranı (0-1) |
| `payment.retry.delay-hours` | `24, 72, 168` | Fatura ödeme retry aralıkları (FR-27) |
| `payment.retry.use-minutes-for-dev` | `true` | Dev ortamında saat yerine dakika kullanır |

## Kafka

**Consume:**

| Topic | Event | Davranış |
|---|---|---|
| `order.created` | OrderCreated | Mock ödeme dener; `processed_events` ile idempotent |
| `invoice.generated` | InvoiceGenerated | Otomatik fatura ödemesi başlatır (doc 8.7) |

**Publish (outbox üzerinden, 5 sn'de bir):**

| Topic | Event | İçerik |
|---|---|---|
| `payment.completed` | PaymentCompleted | eventId, paymentId, orderId, invoiceId, customerId, amount, currency, status, paidAt, tariffCode, minutesIncluded, smsIncluded, dataMbIncluded |
| `payment.failed` | PaymentFailed | eventId, paymentId, orderId, customerId, amount, currency, status, reason, failedAt |
| `payment.refunded` | PaymentRefunded | eventId, paymentId, invoiceId, customerId, amount, currency, refundedAt |

## Özellikler

- **Idempotency-Key** (FR-26): `POST /api/v1/payments` isteğinde `Idempotency-Key` header'ı ile tekrarlayan istekler engellenir
- **Wallet** (FR-25): `method=WALLET` ile müşteri cüzdanından tahsilat
- **Retry scheduler** (FR-27): Fatura ödemesi başarısız olursa 24/72/168 saat (dev: dakika) sonra otomatik yeniden dener

## Endpoints

| Method | Path | Açıklama |
|---|---|---|
| POST | `/api/v1/payments` | Fatura ödemesi oluştur (`Idempotency-Key` opsiyonel) |
| POST | `/api/v1/payments/{id}/process` | Ödemeyi işle |
| POST | `/api/v1/payments/{id}/refund` | İade |
| GET | `/api/v1/payments/{id}` | Ödeme detayı |
| GET | `/api/v1/payments/invoice/{invoiceId}` | Faturaya ait ödemeler |
| GET | `/api/v1/payments/{id}/attempts` | Ödeme denemeleri |

Swagger UI: `http://localhost:9008/swagger-ui.html`

## Tablolar

`payments`, `payment_attempts`, `wallets`, `audit_log`, `outbox_events`, `processed_events`

## Test

DB/Kafka gerektirmeyen Mockito unit testleri: `mvnw -pl payment-service test`
