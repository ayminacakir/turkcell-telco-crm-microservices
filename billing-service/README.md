# Billing Service

Fatura döngüsü (BillCycle) ve fatura (Invoice) yönetimi. `subscription.activated`
event'i ile fatura döngüsü açar, zamanlanmış bill-run job'ı ile fatura keser ve
`InvoiceGenerated` event'ini **Kafka Outbox Pattern** ile yayınlar.

## Port / Veritabanı

| | |
|---|---|
| Port | 9007 |
| DB | `billing_db` (schema: `billing_service`) |
| Migration | Flyway — `src/main/resources/db/migration/V1__init_schema.sql` |

## Konfigürasyon

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka adresi |
| `spring.flyway.enabled` | `true` | Flyway migration |

## Kafka

**Consume:**

| Topic | Event | Davranış |
|---|---|---|
| `subscription.activated` | SubscriptionActivated | BillCycle oluşturur; `processed_events` ile idempotent |
| `payment.completed` | PaymentCompleted | invoiceId doluysa faturayı PAID/OVERDUE yapar; idempotent |

**Publish (outbox üzerinden, 5 sn'de bir):**

| Topic | Event | İçerik |
|---|---|---|
| `invoice.generated` | InvoiceGenerated | eventId, invoiceId, customerId, subscriptionId, periodStart, periodEnd, grandTotal, dueDate, issuedAt |

## Bill-Run

- Scheduler: her gün gece yarısı (`0 0 0 * * *`), `nextRunDate <= bugün` olan döngüler için fatura keser.
- Manuel tetikleme: `POST /api/v1/billing/runs`
- Not: usage aggregation henüz bağlı olmadığı için fatura tutarları şimdilik sıfır kesilir.

## Endpoints

| Method | Path | Açıklama |
|---|---|---|
| POST | `/api/v1/billing/runs` | Bill-run'ı manuel tetikle |
| Bill cycle / invoice CRUD | `BillCycleController`, `InvoiceController` | Mevcut endpoint'ler |

Swagger UI: `http://localhost:9007/swagger-ui.html`

## Tablolar

`bill_cycles`, `invoices`, `invoice_lines`, `outbox_events`, `processed_events`

## Test

DB/Kafka gerektirmeyen Mockito unit testleri: `mvnw -pl billing-service test`
