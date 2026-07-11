# Payment Service

Sipariş ve fatura ödemelerini yöneten servis. Mock payment gateway ile ödeme dener,
sonucu **Kafka Outbox Pattern** ile yayınlar ve tüm ödeme işlemlerini `audit_log`
tablosuna kaydeder.

## Port / Veritabanı

| | |
|---|---|
| Port | 9008 |
| DB | `payment_db` (schema: `payment_service`) |
| Migration | Flyway — `src/main/resources/db/migration/V1__init_schema.sql` |

## Konfigürasyon

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka adresi |
| `spring.flyway.enabled` | `true` | Flyway migration |
| `payment.gateway.success-rate` | `0.8` | Mock gateway başarı oranı (0-1) |

## Kafka

**Consume:**

| Topic | Event | Davranış |
|---|---|---|
| `order.created` | OrderCreated | Mock ödeme dener; `processed_events` ile idempotent |

**Publish (outbox üzerinden, 5 sn'de bir):**

| Topic | Event | İçerik |
|---|---|---|
| `payment.completed` | PaymentCompleted | eventId, paymentId, orderId, invoiceId, customerId, amount, currency, status, paidAt, tariffCode, minutesIncluded, smsIncluded, dataMbIncluded |
| `payment.failed` | PaymentFailed | eventId, paymentId, orderId, customerId, amount, currency, status, reason, failedAt |

## Endpoints

| Method | Path | Açıklama |
|---|---|---|
| POST | `/api/v1/payments` | Fatura ödemesi oluştur |
| POST | `/api/v1/payments/{id}/process` | Ödemeyi işle (maks. 3 deneme) |
| POST | `/api/v1/payments/{id}/refund` | İade |
| GET | `/api/v1/payments/{id}` | Ödeme detayı |
| GET | `/api/v1/payments/invoice/{invoiceId}` | Faturaya ait ödemeler |
| GET | `/api/v1/payments/{id}/attempts` | Ödeme denemeleri |

Swagger UI: `http://localhost:9008/swagger-ui.html`

## Tablolar

`payments`, `payment_attempts`, `audit_log`, `outbox_events`, `processed_events`

## Test

DB/Kafka gerektirmeyen Mockito unit testleri: `mvnw -pl payment-service test`
