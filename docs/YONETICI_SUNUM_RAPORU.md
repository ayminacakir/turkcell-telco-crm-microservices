# Turkcell Telco CRM Microservices Platformu
## Yönetici Sunum Raporu

**Proje:** Turkcell "Geleceği Yazanlar" Bootcamp — TelcoX CRM MVP  
**Mimari:** Microservices + Event-Driven + Database-per-Service  
**Teknoloji:** Java 21 · Spring Boot 3.4 · Spring Cloud · PostgreSQL · Kafka · Docker  
**Rapor Tarihi:** Temmuz 2026  
**Versiyon:** 1.0

---

## 1. Yönetici Özeti

TelcoX CRM, hayali bir GSM operatörünün abone yaşam döngüsünü uçtan uca dijitalleştirmek için tasarlanmış, **ölçeklenebilir ve event-driven** bir mikroservis platformudur. Proje; müşteri kaydından siparişe, abonelik aktivasyonundan faturalandırma ve ödemeye, kullanım takibinden bildirim ve destek taleplerine kadar telekom operatörlerinin temel CRM süreçlerini modern yazılım mimarisi ile modellemektedir.

### Temel Başarılar

| Alan | Durum |
|------|-------|
| Mikroservis sayısı | 9 iş servisi + 3 altyapı bileşeni |
| Uçtan uca abone onboarding akışı | Tamamlandı (Saga + Kafka) |
| Aylık faturalandırma (bill-run) | Tamamlandı |
| Ödeme + retry + idempotency | Tamamlandı |
| Kullanım/kota takibi + CDR simülasyonu | Tamamlandı |
| Güvenlik (JWT, rate limit, audit) | MVP seviyesinde tamamlandı |
| Transactional Outbox Pattern | 9 serviste uygulandı |
| Docker altyapısı | Kafka, PostgreSQL, Redis, Keycloak hazır |

### İş Değeri

- Monolitik CRM sistemlerinin parçalanarak **bağımsız ölçeklenebilir** servislere dönüştürülmesi
- Yüksek hacimli telekom verilerinin (CDR, fatura, ödeme) **asenkron ve dayanıklı** işlenmesi
- Regülasyon uyumu için **audit log**, **PII şifreleme** ve **KVKK uyumlu soft-delete** altyapısı
- Self-servis kanallar ve çağrı merkezi için **standart REST API** sözleşmeleri

---

## 2. Proje Vizyonu ve Kapsam

### 2.1 Vizyon

Bir GSM operatörünün abonelerine yönelik tüm yaşam döngüsü süreçlerini — müşteri kaydı, ürün siparişi, faturalandırma, kullanım takibi, müşteri destek — tek bir mikroservis ekosistemi üzerinden yönetebilen, ölçeklenebilir ve event-driven bir CRM platformu.

### 2.2 MVP Kapsamı (Scope In)

| Senaryo | Açıklama |
|---------|----------|
| Bireysel müşteri kaydı ve KYC | Kimlik doğrulama, adres/belge yönetimi |
| Postpaid tarife siparişi ve aktivasyon | Saga orchestration ile uçtan uca |
| Aylık faturalandırma | Sabit ücret + aşım kalemleri |
| Kredi kartı ile ödeme | Mock PSP entegrasyonu |
| SMS ve e-posta bildirimleri | Şablon tabanlı, Kafka-driven |
| Kota görüntüleme ve eşik bildirimleri | %80 ve %100 kullanım uyarıları |
| Temel müşteri ticketing | SLA takibi dahil |
| Ürün katalog CRUD | Tarife ve addon yönetimi |

### 2.3 MVP Kapsamı Dışında Bırakılanlar

Prepaid top-up, numara taşıma (MNP), kurumsal müşteri yönetimi, kampanya motoru, BTK regülasyon raporları, roaming ve mobil uygulama (backend + Swagger UI ile sınırlandırıldı).

---

## 3. Mimari Genel Bakış

### 3.1 Mantıksal Mimari

```
[ Web / Mobil İstemci / Swagger UI ]
              │
              ▼
    ┌─────────────────────┐
    │    API Gateway      │  ← JWT doğrulama, rate limit, routing
    │      (8080)         │
    └─────────────────────┘
              │
    ┌─────────┴──────────────────────────────────┐
    │  Eureka (8761)  │  Config Server (8888)    │
    └─────────┬──────────────────────────────────┘
              │
    ┌─────────┼─────────┬──────────┬────────────┐
    ▼         ▼         ▼          ▼            ▼
 identity  customer  catalog    order    subscription
              │         │          │            │
              └─────────┴──────────┴────────────┘
                            │
                      [ Kafka Bus ]
                            │
    ┌─────────┬─────────┬──────────┬────────────┐
    ▼         ▼         ▼          ▼            ▼
 billing   payment  notification  ticket    usage
```

### 3.2 Mimari Prensipler

| Prensip | Uygulama |
|---------|----------|
| **Database per Service** | Her servisin kendi PostgreSQL veritabanı ve şeması |
| **Bounded Context (DDD)** | Her servis kendi domain agregatlarına sahip |
| **Eventual Consistency** | Kafka + Transactional Outbox Pattern |
| **API Gateway Pattern** | Tek giriş noktası, merkezi güvenlik |
| **Saga Pattern** | Çok adımlı sipariş süreçlerinde orchestration |
| **Idempotent Consumer** | `processed_events` tablosu ile tekrar işleme koruması |
| **Circuit Breaker** | Order servisinde Resilience4j (Feign çağrıları) |

### 3.3 Servis Envanteri

| Servis | Port | Bounded Context | Veritabanı |
|--------|------|-----------------|------------|
| api-gateway | 8080 | Edge routing, güvenlik | — |
| discovery-server (Eureka) | 8761 | Servis kaydı | — |
| config-server | 8888 | Merkezi konfigürasyon | — |
| customer-service | 9002 | Müşteri yönetimi | customer_db (5432) |
| product-catalog-service | 9003 | Ürün katalogu | product_catalog_db (5433) |
| order-service | 9004 | Sipariş orkestrasyonu | order_db (5434) |
| subscription-service | 9005 | Abonelik yaşam döngüsü | subscription_db (5435) |
| usage-service | 9006 | Kullanım ve kota | usage_db (5436) |
| billing-service | 9007 | Fatura üretimi | billing_db (5437) |
| payment-service | 9008 | Ödeme işlemleri | payment_db (5438) |
| notification-service | 9009 | Bildirim | notification_db (5439) |
| ticket-service | 9010 | Müşteri talepleri | ticket_db (5440) |

---

## 4. Servis Detayları

### 4.1 Customer Service (9002) — Müşteri Yönetimi

**Sorumluluk:** Müşterinin kimlik ve iletişim bilgilerinin master kaydı.

**Temel API'ler:**
- `POST /api/v1/customers` — Yeni müşteri kaydı
- `GET/PUT/DELETE /api/v1/customers/{id}` — CRUD + soft-delete (KVKK)
- `POST /api/v1/customers/{id}/kyc/approve|reject` — KYC süreci (ADMIN)
- `POST/GET /api/v1/customers/{id}/addresses|documents|contacts`

**Güvenlik:** JWT OAuth2, TCKN AES-GCM şifreleme, audit log.

**Yayınlanan Event'ler:** `customer.registered`, `customer.kyc.approved`, `customer.kyc.rejected`, `customer.updated`

---

### 4.2 Product Catalog Service (9003) — Ürün Kataloğu

**Sorumluluk:** Tarife, addon ve VAS ürünlerinin master katalog yönetimi.

**Temel API'ler:**
- `GET/POST /api/v1/tariffs` — Tarife listeleme ve oluşturma
- `GET/POST /api/v1/addons` — Addon yönetimi
- `GET /api/v1/addons?tariffCode=...` — Tarifeye bağlı addonlar

**Yayınlanan Event'ler:** `tariff.created`, `tariff.price.changed`

---

### 4.3 Order Service (9004) — Sipariş Orkestrasyonu

**Sorumluluk:** Sipariş alma ve Saga ile çoklu servis koordinasyonu.

**Temel API'ler:**
- `POST /api/v1/orders` — Yeni sipariş
- `GET /api/v1/orders/{id}` — Sipariş sorgulama
- `POST /api/v1/orders/{id}/cancel` — İptal + kompansasyon

**Sipariş Durumları:** `DRAFT` → `PENDING_PAYMENT` → `PAID` → `FULFILLED` / `CANCELLED`

**Saga State Machine:** `STARTED` → `PAYMENT_PENDING` → `PAYMENT_COMPLETED` → `SUBSCRIPTION_PENDING` → `COMPLETED` / `FAILED` / `COMPENSATED`

**Dış Bağımlılıklar:** OpenFeign ile customer-service ve product-catalog-service (Resilience4j circuit breaker).

**OrderItem Migrasyonu:** `productId` yerine `productCode` + `productType` (TARIFF/ADDON) zorunlu alanlar; ürün doğrulaması Feign ile yapılır.

**Yayınlanan Event'ler:** `order.created`, `order.confirmed`, `order.cancelled`  
**Tüketilen Event'ler:** `payment.completed`, `payment.failed`, `subscription.activated`

---

### 4.4 Subscription Service (9005) — Abonelik Yönetimi

**Sorumluluk:** Abonelik state machine, MSISDN tahsisi, SIM kart yönetimi.

**Temel API'ler:**
- `POST /api/v1/subscriptions` — Abonelik oluşturma (internal)
- `GET /api/v1/subscriptions/{id}` — Abonelik sorgulama
- `POST /api/v1/subscriptions/{id}/suspend|reactivate|terminate`
- `POST /api/v1/subscriptions/msisdns` — MSISDN havuzu yönetimi
- `PATCH /api/v1/subscriptions/{id}/mnp-status` — MNP durumu (gelecek faz)

**Abonelik Durumları:** `ACTIVE` → `SUSPENDED` → `TERMINATED`

**Güvenlik:** Audit log (her değişiklik kayıt altında).

**Yayınlanan Event'ler:** `subscription.activated`, `subscription.suspended`, `subscription.terminated`  
**Tüketilen Event'ler:** `payment.completed`, `payment.failed`

---

### 4.5 Usage Service (9006) — Kullanım ve Kota Takibi

**Sorumluluk:** CDR eventlerini tüketip kullanım sayaçlarını günceller; kota aşımı bildirimleri üretir.

**Temel API'ler:**
- `GET /api/v1/usage/subscriptions/{id}/quota` — Aktif kota görüntüleme
- `GET /api/v1/usage/subscriptions/{id}/history` — Kullanım geçmişi
- `POST /api/v1/usage/records` — Manuel kullanım kaydı
- `POST /api/v1/usage/quotas` — Kota oluşturma (ADMIN)

**CDR Simülatörü:** Geliştirme ortamında periyodik sahte CDR üretimi (`usage.cdr-simulator.enabled`).

**Güvenlik:** JWT OAuth2, Correlation-Id filter, `@PreAuthorize` admin endpoint'lerde.

**Yayınlanan Event'ler:** `quota.threshold.reached`, `quota.exceeded`, `usage.aggregated`  
**Tüketilen Event'ler:** `subscription.activated`, `cdr.recorded`

---

### 4.6 Billing Service (9007) — Faturalandırma

**Sorumluluk:** Aylık bill-run scheduler, fatura üretimi, PDF çıktısı.

**Temel API'ler:**
- `GET /api/v1/invoices?customerId=...` — Fatura listeleme
- `GET /api/v1/invoices/{id}` — Fatura detayı
- `GET /api/v1/invoices/{id}/pdf` — Fatura PDF indirme
- `POST /api/v1/billing/runs` — Manuel bill-run tetikleme (ADMIN)
- `POST/GET /api/v1/bill-cycles` — Fatura dönemi yönetimi

**Fatura Kalemleri:** Aylık ücret, addon ücretleri, aşım (voice/SMS/data), vergiler.

**Scheduler:** Her gece yarısı otomatik bill-run (`BillingRunService`).

**Güvenlik:** JWT OAuth2, Correlation-Id filter, `@PreAuthorize` admin endpoint'lerde.

**Yayınlanan Event'ler:** `invoice.generated`, `invoice.paid`, `invoice.overdue`  
**Tüketilen Event'ler:** `subscription.activated`, `payment.completed`, `usage.aggregated`

---

### 4.7 Payment Service (9008) — Ödeme İşlemleri

**Sorumluluk:** Ödeme alma, mock PSP entegrasyonu, cüzdan, retry mekanizması.

**Temel API'ler:**
- `POST /api/v1/payments` — Ödeme oluşturma (Idempotency-Key header destekli)
- `GET /api/v1/payments/{id}` — Ödeme sorgulama
- `POST /api/v1/payments/{id}/process` — Ödeme işleme
- `POST /api/v1/payments/{id}/refund` — İade (ADMIN)
- `GET /api/v1/payments/{id}/attempts` — Deneme geçmişi

**Ödeme Yöntemleri:** Kredi kartı, banka transferi, cüzdan (FR-25).

**Idempotency:** Aynı `paymentRequestId` iki kez işlenmez (FR-26).

**Retry Politikası:** Başarısız ödemelerde 24/72/168 saat aralıkla yeniden deneme (FR-27); dev ortamında dakika bazlı.

**Flyway Migrasyonları:**
- `V1__init_schema.sql` — Temel tablolar
- `V2__add_retry_wallet_idempotency.sql` — Retry, cüzdan, idempotency
- `V3__add_missing_payment_columns.sql` — Eski Hibernate tabloları için eksik kolon düzeltmesi
- `db/callback/beforeMigrate.sql` — V1 öncesi legacy DB uyumluluğu

**Güvenlik:** JWT OAuth2, Correlation-Id filter, audit log, `@PreAuthorize` refund endpoint'inde.

**Yayınlanan Event'ler:** `payment.completed`, `payment.failed`, `payment.refunded`  
**Tüketilen Event'ler:** `order.created`, `invoice.generated`

---

### 4.8 Notification Service (9009) — Bildirim

**Sorumluluk:** SMS, e-posta ve push bildirim gönderimi; şablon tabanlı eşleştirme.

**Temel API'ler:**
- `POST /api/v1/notifications` — Bildirim gönderme (internal)
- `GET /api/v1/notifications/users/{id}/history` — Bildirim geçmişi
- `POST/GET /api/v1/notification-templates` — Şablon yönetimi

**Tüketilen Event'ler (Kafka-driven):** `customer.registered`, `subscription.activated`, `invoice.generated`, `payment.completed`, `payment.failed`, `quota.threshold.reached`, `quota.exceeded`

---

### 4.9 Ticket Service (9010) — Müşteri Talepleri

**Sorumluluk:** Şikayet, talep ve arıza kaydı; SLA takibi.

**Temel API'ler:**
- `POST /api/v1/tickets` — Talep açma
- `GET /api/v1/tickets/{id}` — Talep sorgulama
- `POST /api/v1/tickets/{id}/comments` — Yorum ekleme
- `POST /api/v1/tickets/{id}/assign|resolve` — Atama ve çözüm

**SLA Scheduler:** 60 saniyede bir SLA ihlali kontrolü.

**Yayınlanan Event'ler:** `ticket.opened`, `ticket.resolved`, `ticket.sla.breached`

---

## 5. Uçtan Uca İş Akışları

### 5.1 Yeni Hat Siparişi (Happy Path)

```
1. Müşteri → POST /orders → Order Service
2. Order Service: Saga=PAYMENT_PENDING, order.created → Kafka
3. Payment Service: order.created tüketir → mock ödeme
4. Payment Service: payment.completed → Kafka
5. Order Service: Sipariş=PAID, Saga=PAYMENT_COMPLETED
6. Subscription Service: payment.completed → MSISDN tahsis + abonelik oluştur
7. Subscription Service: subscription.activated → Kafka
8. Order Service: Sipariş=FULFILLED, Saga=COMPLETED
9. Usage Service: Kota oluşturulur
10. Billing Service: Bill cycle oluşturulur
11. Notification Service: Hoş geldin SMS/e-posta gönderilir
```

### 5.2 Kompansasyon (Failure Path)

```
Ödeme başarısız → payment.failed
  → Order Service: Sipariş=CANCELLED, Saga=COMPENSATED
  → order.cancelled eventi yayınlanır

Abonelik aktivasyonu başarısız
  → Payment Service: refund tetiklenir
  → Order Service: Sipariş=CANCELLED
```

### 5.3 Aylık Faturalandırma

```
1. BillingRunService (cron: gece yarısı) tetiklenir
2. Aktif abonelikler için fatura üretilir
3. Kullanım aşım kalemleri usage.aggregated eventinden gelir
4. invoice.generated → Kafka
5. Notification Service: Fatura bildirimi gönderir
6. Payment Service: invoice.generated → otomatik ödeme denemesi (opsiyonel)
7. payment.completed → invoice.paid eventi
```

### 5.4 Kota Aşımı

```
1. CDR Simulator / gerçek CDR → cdr.recorded → Kafka
2. Usage Service: Kota güncellenir
3. %80 eşik → quota.threshold.reached → SMS uyarısı
4. %100 eşik → quota.exceeded → ek paket SMS
5. Aşım → usage.aggregated → Billing Service fatura kalemine ekler
```

---

## 6. Güvenlik Mimarisi (Bölüm 13)

### 6.1 Kimlik Doğrulama ve Yetkilendirme

| Katman | Uygulama |
|--------|----------|
| **Kimlik sağlayıcı** | Keycloak (OAuth2/OIDC), realm: `telco-crm` |
| **API Gateway** | Tüm isteklerde JWT doğrulama |
| **Header relay** | Gateway → `X-User-Id`, `X-User-Roles` downstream servislere |
| **Servis seviyesi JWT** | customer, billing, payment, usage, product-catalog, notification, ticket servislerinde OAuth2 resource server |
| **Method security** | `@PreAuthorize("hasRole('ADMIN')")` kritik endpoint'lerde |
| **Rate limiting** | Gateway'de Redis tabanlı, kullanıcı başına 100 req/dk |

### 6.2 Korunan Admin Endpoint'leri

| Servis | Endpoint | Rol |
|--------|----------|-----|
| customer-service | KYC approve/reject | ADMIN |
| billing-service | POST /billing/runs | ADMIN |
| billing-service | POST /bill-cycles/runs | ADMIN |
| payment-service | POST /payments/{id}/refund | ADMIN |
| usage-service | POST /usage/quotas | ADMIN |

### 6.3 Veri Güvenliği

| Önlem | Uygulama | Servis |
|-------|----------|--------|
| PII şifreleme (TCKN) | AES-GCM, Vault key | customer-service |
| Audit log | Her değişiklik `audit_log` tablosuna | customer, payment, subscription |
| Soft-delete | GDPR/KVKK uyumu | customer-service |
| Idempotency | `paymentRequestId` unique constraint | payment-service |
| Idempotent consumer | `processed_events` tablosu | order, payment, subscription, usage, billing, notification |

### 6.4 Gözlemlenebilirlik

| Özellik | Uygulama |
|---------|----------|
| Correlation-Id | billing, payment, usage servislerinde MDC log + hata cevabı |
| Structured errors | RFC 7807 ProblemDetail formatı (`correlationId` alanı dahil) |
| Actuator | Tüm servislerde health/metrics endpoint'leri |
| Kafka UI | Port 9093 — event izleme |

---

## 7. Veri Yönetimi

### 7.1 Database-per-Service Pattern

Her mikroservis yalnızca kendi PostgreSQL veritabanına erişir. Servisler arası doğrudan foreign key kullanılmaz; yalnızca UUID referansları tutulur.

### 7.2 Flyway Migrasyonları

Tüm 9 iş servisinde Flyway ile versiyonlu şema yönetimi:

| Servis | Migration Sayısı | Öne Çıkan |
|--------|------------------|-----------|
| customer-service | 3 | Audit log, PII encryption column |
| product-catalog-service | 2 | Outbox events |
| order-service | 1 | Saga state, productCode/productType |
| subscription-service | 2 | Audit log |
| usage-service | 2 | Kota aşım takibi |
| billing-service | 2 | Usage aggregation |
| payment-service | 3 + callback | Retry, wallet, legacy DB fix |
| notification-service | 3 | Template seeding |
| ticket-service | 2 | SLA flag |

### 7.3 Transactional Outbox Pattern

Tüm 9 iş servisinde uygulandı:

1. İş mantığı + `outbox_events` tablosuna yazma **aynı transaction** içinde
2. `OutboxPublisherService` (5 sn aralıkla) PENDING eventleri Kafka'ya gönderir
3. Başarılı yayın sonrası status → `PUBLISHED`
4. Kafka consumer tarafında `processed_events` ile idempotency

---

## 8. Altyapı ve DevOps

### 8.1 Docker Compose Bileşenleri

| Bileşen | Image | Port | Amaç |
|---------|-------|------|------|
| Kafka | apache/kafka:4.2.0 | 9092 | Event bus |
| Kafka UI | kafbat/kafka-ui | 9093 | Event izleme |
| Keycloak | keycloak:26.1 | 8085 | OAuth2/JWT |
| Redis | redis:7-alpine | 6379 | Rate limiting |
| PostgreSQL × 9 | postgres:17 | 5432–5440 | Servis DB'leri |
| pgAdmin | dpage/pgadmin4 | 5050 | DB yönetimi |

### 8.2 Spring Cloud Altyapısı

| Bileşen | Port | Görev |
|---------|------|-------|
| Eureka Server | 8761 | Servis keşfi |
| Config Server | 8888 | Git-backed merkezi konfigürasyon |
| API Gateway | 8080 | Routing, JWT, rate limit |

### 8.3 Gateway Route'ları

| Path | Hedef Servis |
|------|--------------|
| `/api/v1/customers/**` | customer-service |
| `/api/v1/orders/**` | order-service |
| `/api/v1/subscriptions/**` | subscription-service |
| `/api/v1/billing/**` | billing-service |
| `/api/v1/payments/**` | payment-service |

> **Not:** usage, invoices, bill-cycles, notifications, tickets ve product-catalog endpoint'leri henüz gateway üzerinden route edilmemiştir; doğrudan servis portlarından erişilebilir (Swagger UI).

---

## 9. Teknoloji Yığını

| Katman | Teknoloji | Sürüm |
|--------|-----------|-------|
| Dil | Java (LTS) | 21 |
| Framework | Spring Boot | 3.4.5 |
| Cloud | Spring Cloud (Gateway, Config, Eureka, OpenFeign) | 2024.0.1 |
| Build | Maven Multi-module | — |
| Veritabanı | PostgreSQL | 17 |
| Migration | Flyway | — |
| Messaging | Apache Kafka | 4.2 |
| Cache | Redis | 7 |
| Auth | Keycloak (OAuth2/OIDC) | 26.1 |
| API Docs | springdoc-openapi (Swagger UI) | 2.8.3 |
| Resilience | Resilience4j (Circuit Breaker) | order-service |
| PDF | openhtmltopdf | billing-service |
| Container | Docker Compose | — |

---

## 10. Proje Durumu

### 10.1 Tamamlanan Özellikler

- [x] 9 iş mikroservisi + 3 altyapı bileşeni
- [x] Database-per-service + Flyway migrasyonları
- [x] Transactional Outbox Pattern (9 servis) + FAILED event retry
- [x] Saga orchestration (order-service)
- [x] Uçtan uca abone onboarding akışı
- [x] Aylık faturalandırma + PDF üretimi
- [x] Mock ödeme + retry + idempotency + cüzdan
- [x] CDR simülasyonu + kota takibi + aşım bildirimleri
- [x] Kafka-driven bildirim sistemi (şablon tabanlı, opt-in/opt-out)
- [x] Ticket yönetimi + SLA breach scheduler
- [x] JWT güvenlik (gateway + tüm iş servisleri)
- [x] Rate limiting (gateway, Redis)
- [x] PII şifreleme (customer-service)
- [x] Audit log (customer, payment, subscription)
- [x] Correlation-Id (billing, payment, usage)
- [x] Docker Compose altyapısı
- [x] GitHub Actions CI (build + test)
- [x] Kubernetes örnek deployment (product-catalog)
- [x] Event sözleşmeleri dokümantasyonu (`docs/event-contracts.md`)
- [x] Web arayüz taslakları (`docs/ui/`)
- [x] Swagger UI (tüm servisler)
- [x] Tarife versiyonlama (FR-08), HYBRID tip + segment
- [x] Redis cache (product-catalog)
- [x] Pagination (catalog, notification, ticket)

---

## 11. Demo Senaryoları (Sunum İçin)

### Senaryo 1: Yeni Abone Kaydı ve Hat Aktivasyonu

1. Keycloak'tan JWT token al
2. `POST /api/v1/customers` ile müşteri oluştur
3. `POST /api/v1/customers/{id}/kyc/approve` (ADMIN token)
4. `POST /api/v1/orders` ile tarife siparişi ver
5. Kafka UI'da `order.created` → `payment.completed` → `subscription.activated` akışını izle
6. `GET /api/v1/orders/{id}` → status: `FULFILLED`
7. `GET /api/v1/usage/subscriptions/{id}/quota` → kota görüntüle

### Senaryo 2: Aylık Faturalandırma

1. `POST /api/v1/billing/runs` (ADMIN) ile bill-run tetikle
2. `GET /api/v1/invoices?customerId=...` ile fatura listele
3. `GET /api/v1/invoices/{id}/pdf` ile PDF indir
4. Kafka'da `invoice.generated` eventini doğrula

### Senaryo 3: Ödeme ve Retry

1. `POST /api/v1/payments` ile ödeme oluştur
2. `POST /api/v1/payments/{id}/process` ile işle
3. Başarısız ödemede retry scheduler'ın devreye girdiğini loglardan izle
4. `GET /api/v1/payments/{id}/attempts` ile deneme geçmişini gör

### Senaryo 4: Kota Aşımı Bildirimi

1. CDR simulator'ü aktifleştir (`usage.cdr-simulator.enabled=true`)
2. Kafka'da `cdr.recorded` eventlerini izle
3. Kota %80'e ulaşınca `quota.threshold.reached` → SMS bildirimi
4. Kota %100'e ulaşınca `quota.exceeded` → ek paket SMS

---

## 12. Ekip ve Sorumluluk Alanları

Proje 3 kişilik ekip tarafından geliştirilmiştir. Her geliştirici 3 mikroservisten sorumludur; altyapı bileşenleri (gateway, Eureka, config-server, Docker Compose, CI) ortak çalışma ile tamamlanmıştır.

| Geliştirici | Sorumlu Servisler | Kapsam |
|-------------|-------------------|--------|
| **Aymina Çakır** | customer-service, subscription-service, order-service | Müşteri CRUD, KYC, PII şifreleme; abonelik lifecycle, MSISDN/SIM; Saga orchestration, productCode migration |
| **Mervenur Küçükkara** | billing-service, payment-service, usage-service | Fatura üretimi, bill-run, PDF; mock PSP, retry, idempotency, cüzdan, Flyway migration; kota takibi, CDR tüketimi, aşım bildirimleri |
| **Nasrulla Emin** | product-catalog-service, notification-service, ticket-service | Tarife/addon katalog, versiyonlama, Redis cache; Kafka-driven bildirim, şablon yönetimi; destek talepleri, SLA |

### Ortak Altyapı Katkıları

| Bileşen | Katkı |
|---------|-------|
| API Gateway | JWT doğrulama, rate limiting, routing |
| Discovery + Config Server | Servis keşfi ve merkezi konfigürasyon |
| Docker Compose | Kafka, PostgreSQL, Redis, Keycloak |
| GitHub Actions CI | Build + unit test pipeline |
| Event sözleşmeleri | `docs/event-contracts.md` |
| Kubernetes demo | `k8s/product-catalog/` |
| Web arayüz taslakları | `docs/ui/` (admin + abone paneli) |

---

## 13. Sonuç

TelcoX CRM Microservices Platformu, telekom operatörlerinin temel CRM süreçlerini modern yazılım mimarisi prensipleriyle başarıyla modellemektedir. Proje;

- **12 bağımsız modül** (9 iş + 3 altyapı) ile modüler ve ölçeklenebilir bir yapı sunar
- **Event-driven mimari** ile servisler arası gevşek bağlılık (loose coupling) sağlar
- **Saga pattern** ile dağıtık transaction yönetimini gerçekleştirir
- **Güvenlik, audit ve PII koruması** ile regülasyon uyumuna hazır altyapı oluşturur
- **Docker Compose** ile tek komutla lokal geliştirme ortamı sunar

Platform MVP kapsamındaki **33 fonksiyonel gereksinimin tamamını** karşılamaktadır. Abone onboarding, aylık faturalandırma ve kota aşımı senaryoları uçtan uca çalışır durumdadır.

---

*Bu rapor, proje kod tabanı ve MVP Analiz Dokümanı (v1.0) esas alınarak hazırlanmıştır.*
