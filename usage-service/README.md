# Usage Service

Kota (Quota) ve CDR kullanım kayıtları (UsageRecord). `subscription.activated`
event'i ile kota açar; CDR event'lerini Kafka üzerinden işler, kullanımda kota düşer,
%80/%100 eşiklerinde ve aşım durumunda event'leri **Kafka Outbox Pattern** ile yayınlar.

## Port / Veritabanı

| | |
|---|---|
| Port | 9006 |
| DB | `usage_db` (schema: `usage_service`) |
| Migration | Flyway — `V1__init_schema.sql`, `V2__add_quota_overage_tracking.sql` |

## Konfigürasyon

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka adresi |
| `spring.flyway.enabled` | `true` | Flyway migration |
| `usage.cdr-simulator.enabled` | `false` | CDR simülatörünü açar (Senaryo 3 testi) |
| `usage.cdr-simulator.fixed-delay-ms` | `30000` | Simülatör üretim aralığı |

## Kafka

**Consume:**

| Topic | Event | Davranış |
|---|---|---|
| `subscription.activated` | SubscriptionActivated | Quota oluşturur; `processed_events` ile idempotent |
| `cdr.recorded` | CdrRecorded | CDR kullanımını işler; kota düşer (FR-17) |

**Publish (outbox üzerinden, 5 sn'de bir):**

| Topic | Event | Ne zaman |
|---|---|---|
| `quota.threshold.reached` | QuotaThresholdReached | Kullanım %80'i geçince |
| `quota.exceeded` | QuotaExceeded | Kota tükenince (%100) |
| `usage.aggregated` | UsageAggregated | Aşım oluştuğunda billing'e iletilir (FR-20) |

## CDR Simülatörü (FR-17)

`usage.cdr-simulator.enabled=true` yapıldığında, aktif kotası olan rastgele bir
abonelik için periyodik olarak rastgele VOICE/SMS/DATA kullanımı üretir ve
`cdr.recorded` topic'ine yazar (`CDR-SIM-xxxx` referanslı). Usage Service bu
event'i consume ederek kullanımı işler.

## Endpoints

Mevcut `UsageController` endpoint'leri (kullanım kaydı, geçmiş, aktif kota).

Swagger UI: `http://localhost:9006/swagger-ui.html`

## Tablolar

`usage_records`, `quotas`, `outbox_events`, `processed_events`

## Test

DB/Kafka gerektirmeyen Mockito unit testleri: `mvnw -pl usage-service test`
