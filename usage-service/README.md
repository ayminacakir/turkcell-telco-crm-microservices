# Usage Service

Kota (Quota) ve CDR kullanım kayıtları (UsageRecord). `subscription.activated`
event'i ile kota açar; kullanım kaydında kota düşer, %80 ve %100 eşiklerinde
event'leri **Kafka Outbox Pattern** ile yayınlar.

## Port / Veritabanı

| | |
|---|---|
| Port | 9006 |
| DB | `usage_db` (schema: `usage_service`) |
| Migration | Flyway — `src/main/resources/db/migration/V1__init_schema.sql` |

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

**Publish (outbox üzerinden, 5 sn'de bir):**

| Topic | Event | Ne zaman |
|---|---|---|
| `quota.threshold.reached` | QuotaThresholdReached | Kullanım %80'i geçince |
| `quota.exceeded` | QuotaExceeded | Kota tükenince (%100) |

## CDR Simülatörü

`usage.cdr-simulator.enabled=true` yapıldığında, aktif kotası olan rastgele bir
abonelik için periyodik olarak rastgele VOICE/SMS/DATA kullanımı üretir
(`CDR-SIM-xxxx` referanslı). Kota aşımı senaryosunu uçtan uca test etmek içindir.

## Endpoints

Mevcut `UsageController` endpoint'leri (kullanım kaydı, geçmiş, aktif kota).

Swagger UI: `http://localhost:9006/swagger-ui.html`

## Tablolar

`usage_records`, `quotas`, `outbox_events`, `processed_events`

## Test

DB/Kafka gerektirmeyen Mockito unit testleri: `mvnw -pl usage-service test`
