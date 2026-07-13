# Billing Service

Fatura döngüsü (BillCycle) ve fatura (Invoice) yönetimi. `subscription.activated`
event'i ile fatura döngüsü açar, zamanlanmış bill-run job'ı ile fatura keser,
usage aşım verilerini billing'e alır ve event'leri **Kafka Outbox Pattern** ile yayınlar.

## Port / Veritabanı

| | |
|---|---|
| Port | 9007 |
| DB | `billing_db` (schema: `billing_service`) |
| Migration | Flyway — `V1__init_schema.sql`, `V2__add_usage_aggregation_and_tariff.sql` |

## Konfigürasyon

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka adresi |
| `spring.flyway.enabled` | `true` | Flyway migration |
| `billing.default-monthly-fee` | `149.90` | Varsayılan aylık tarife |
| `billing.tax-rate` | `0.20` | KDV oranı |
| `billing.tariffs.TARIFF-BASIC` | `149.90` | Tarife kodu → ücret eşlemesi |
| `billing.overage.voice-per-minute` | `0.50` | Dakika aşım birim fiyatı |
| `billing.overage.sms-per-unit` | `0.25` | SMS aşım birim fiyatı |
| `billing.overage.data-per-mb` | `0.10` | Data aşım birim fiyatı (MB) |

## Kafka

**Consume:**

| Topic | Event | Davranış |
|---|---|---|
| `subscription.activated` | SubscriptionActivated | BillCycle oluşturur; `processed_events` ile idempotent |
| `payment.completed` | PaymentCompleted | invoiceId doluysa faturayı PAID/OVERDUE yapar; idempotent |
| `usage.aggregated` | UsageAggregated | Dönem aşım verilerini `usage_aggregations` tablosuna yazar |

**Publish (outbox üzerinden, 5 sn'de bir):**

| Topic | Event | Ne zaman |
|---|---|---|
| `invoice.generated` | InvoiceGenerated | Bill-run sonrası fatura kesildiğinde |
| `invoice.paid` | InvoicePaid | Ödeme tamamlandığında fatura PAID olunca |
| `invoice.overdue` | InvoiceOverdue | Ödeme başarısız olduğunda fatura OVERDUE olunca |

## Bill-Run (FR-21 / FR-22)

- Scheduler: her gün gece yarısı (`0 0 0 * * *`), `nextRunDate <= bugün` olan döngüler için fatura keser.
- Manuel tetikleme: `POST /api/v1/billing/runs`
- Fatura satırları: aylık tarife + usage aşım (dakika/SMS/data) + KDV
- Fatura durumu: `ISSUED`

## Endpoints

| Method | Path | Açıklama |
|---|---|---|
| POST | `/api/v1/billing/runs` | Bill-run'ı manuel tetikle |
| GET | `/api/v1/invoices/{id}/pdf` | Fatura PDF indir (FR-23) |
| Bill cycle / invoice CRUD | `BillCycleController`, `InvoiceController` | Mevcut endpoint'ler |

Swagger UI: `http://localhost:9007/swagger-ui.html`

## Tablolar

`bill_cycles`, `invoices`, `invoice_lines`, `usage_aggregations`, `outbox_events`, `processed_events`

## Test

DB/Kafka gerektirmeyen Mockito unit testleri: `mvnw -pl billing-service test`
