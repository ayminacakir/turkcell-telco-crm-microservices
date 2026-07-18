<div align="center">

# 📡 Turkcell Telco CRM Microservices

**Turkcell "Geleceği Yazanlar" Bootcamp — 2026**

*Hayali bir GSM operatörü TelcoX için mikroservis tabanlı CRM platformu*

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-green?style=flat-square&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-blue?style=flat-square&logo=postgresql)
![Kafka](https://img.shields.io/badge/Kafka-4.2-231F20?style=flat-square&logo=apachekafka)
![Maven](https://img.shields.io/badge/Maven-Multi--module-red?style=flat-square&logo=apachemaven)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?style=flat-square&logo=docker)

</div>

---

## 📋 İçindekiler

- [Proje Hakkında](#-proje-hakkında)
- [MVP Senaryoları](#-mvp-senaryoları)
- [Mimari](#-mimari)
- [Mikroservisler](#-mikroservisler)
- [Veri Modeli](#-veri-modeli)
- [Tech Stack](#-tech-stack)
- [Kurulum](#-kurulum)
- [Dokümantasyon](#-dokümantasyon)
- [ER Diyagramları](#-er-diyagramları)
- [Proje Durumu](#-proje-durumu)
- [Ekip](#-ekip)

---

## 📖 Proje Hakkında

TelcoX CRM, bir GSM operatörünün tüm iş süreçlerini yöneten **mikroservis tabanlı** kurumsal bir CRM platformudur. Müşteri kaydından faturaya, ödemeden bildirimlere kadar uçtan uca akışı kapsar.

**Temel prensipler:**
- 🔒 **Database per service** — Her mikroservis yalnızca kendi veritabanına erişir
- 🔗 **Loose coupling** — Servisler arası doğrudan FK kullanılmaz; ID referansı tutulur
- 📨 **Event-driven** — Servisler arası iletişim Kafka üzerinden planlanmıştır
- 🏗️ **Multi-module Maven** — Tüm servisler tek repo, ortak parent pom

> 📄 Detaylı analiz ve tasarım dokümanı: [**Proje Gereksinimleri**](docs/PROJE_GEREKSINIMLERI.md) · Demo rehberi: [**TelcoX Web Arayüzü**](docs/DEMO.md) · Event sözleşmeleri: [**event-contracts.md**](docs/event-contracts.md)

---

## 🎯 MVP Senaryoları

```
1. Yeni Abone Onboarding
   Müşteri Kaydı → KYC → Sipariş → Ödeme → Aktivasyon → SMS Bildirimi

2. Aylık Fatura
   Bill-Run Tetiklenir → Kullanım Toplanır → Fatura Oluşur → Bildirim → Ödeme

3. Kota Aşımı
   CDR Gelir → Kota Güncellenir → %80'de Uyarı SMS → %100'de Ek Paket SMS
```

---

## 🏗️ Mimari

```
turkcell-telco-crm-microservices/
│
├── 🌐 gateway_server/            Port: 8080  │  API Gateway + TelcoX web arayüzü
├── 🔍 eureka_server/             Port: 8761  │  Servis keşfi
├── ⚙️  config-server/             Port: 8888  │  Merkezi konfigürasyon
│
├── 📦 customer-service/          Port: 9002  │  DB: customer_db
├── 📦 product-catalog-service/   Port: 9003  │  DB: product_catalog_db
├── 📦 order-service/             Port: 9004  │  DB: order_db
├── 📦 subscription-service/      Port: 9005  │  DB: subscription_db
├── 📦 usage-service/             Port: 9006  │  DB: usage_db
├── 📦 billing-service/           Port: 9007  │  DB: billing_db
├── 📦 payment-service/           Port: 9008  │  DB: payment_db
├── 📦 notification-service/      Port: 9009  │  DB: notification_db
├── 📦 ticket-service/            Port: 9010  │  DB: ticket_db
│
├── 🐳 docker/docker-compose.yml  Kafka · PostgreSQL × 9 · Redis · Keycloak
└── 📄 pom.xml                    ← Root parent pom
```

---

## 🧩 Mikroservisler

| # | Servis | Port | Veritabanı | Sorumlu | Açıklama |
|---|---|---|---|---|---|
| 1 | customer-service | 9002 | customer_db | Aymina | Müşteri kaydı, adres, KYC belgesi |
| 2 | product-catalog-service | 9003 | product_catalog_db | Nasrulla | Tarife, ek paket kataloğu |
| 3 | order-service | 9004 | order_db | Aymina | Sipariş yönetimi, Saga pattern |
| 4 | subscription-service | 9005 | subscription_db | Aymina | Abonelik, MSISDN havuzu, SIM kart |
| 5 | usage-service | 9006 | usage_db | Mervenur | Kota takibi, CDR kullanım kayıtları |
| 6 | billing-service | 9007 | billing_db | Mervenur | Fatura üretimi, fatura döngüsü |
| 7 | payment-service | 9008 | payment_db | Mervenur | Ödeme, ödeme girişimleri |
| 8 | notification-service | 9009 | notification_db | Nasrulla | SMS, e-posta, push bildirimleri |
| 9 | ticket-service | 9010 | ticket_db | Nasrulla | Müşteri talep ve şikayetleri |

---

## 🗄️ Veri Modeli

Her servisin entity'leri:

<details>
<summary><b>customer-service</b></summary>

| Entity | Açıklama |
|---|---|
| Customer | Müşteri bilgileri (INDIVIDUAL / CORPORATE) |
| Address | Müşteri adresleri |
| Document | KYC belgeleri (ID_CARD / PASSPORT) |

</details>

<details>
<summary><b>product-catalog-service</b></summary>

| Entity | Açıklama |
|---|---|
| Tariff | Tarife tanımları (POSTPAID / PREPAID) |
| Addon | Ek paketler (DATA / SMS / MINUTES / VAS) |
| TariffAddon | Tarife-Ek paket ilişkisi (many-to-many) |

</details>

<details>
<summary><b>order-service</b></summary>

| Entity | Açıklama |
|---|---|
| Order | Sipariş başlığı |
| OrderItem | Sipariş kalemleri |
| SagaState | Saga adım durumu |

</details>

<details>
<summary><b>subscription-service</b></summary>

| Entity | Açıklama |
|---|---|
| Subscription | Abonelik (ACTIVE / SUSPENDED / TERMINATED) |
| MsisdnPool | MSISDN havuzu (FREE / RESERVED / ALLOCATED) |
| SimCard | SIM kart |

</details>

<details>
<summary><b>usage-service</b></summary>

| Entity | Açıklama |
|---|---|
| Quota | Dönemlik kota bilgisi |
| UsageRecord | CDR kullanım kayıtları (VOICE / SMS / DATA) |

</details>

<details>
<summary><b>billing-service</b></summary>

| Entity | Açıklama |
|---|---|
| Invoice | Fatura başlığı |
| InvoiceLine | Fatura kalemleri |
| BillCycle | Fatura döngüsü |

</details>

<details>
<summary><b>payment-service</b></summary>

| Entity | Açıklama |
|---|---|
| Payment | Ödeme kaydı |
| PaymentAttempt | Ödeme girişimleri |

</details>

<details>
<summary><b>notification-service</b></summary>

| Entity | Açıklama |
|---|---|
| NotificationTemplate | Bildirim şablonları |
| Notification | Gönderilen bildirimler |

</details>

<details>
<summary><b>ticket-service</b></summary>

| Entity | Açıklama |
|---|---|
| Ticket | Müşteri talep/şikayeti |
| TicketComment | Yorum ve yanıtlar |

</details>

---

## 🛠️ Tech Stack

| Kategori | Teknoloji | Durum |
|---|---|---|
| Dil | Java 21 | ✅ |
| Framework | Spring Boot 3.4 | ✅ |
| Cloud | Spring Cloud Gateway, Config, Eureka, OpenFeign | ✅ |
| ORM | Spring Data JPA / Hibernate | ✅ |
| Veritabanı | PostgreSQL 17 | ✅ |
| Migration | Flyway | ✅ |
| Mesajlaşma | Apache Kafka 4.2 | ✅ |
| Cache / Rate limit | Redis 7 | ✅ |
| Auth | Keycloak (OAuth2/JWT) | ✅ |
| Build | Maven (Multi-module) | ✅ |
| Container | Docker Compose | ✅ |
| CI | GitHub Actions | ✅ |

---

## 🚀 Kurulum

### Gereksinimler

- Java 21+
- Maven 3.9+
- Docker Desktop

### Hızlı Başlangıç

```bash
git clone https://github.com/ayminacakir/turkcell-telco-crm-microservices
cd turkcell-telco-crm-microservices

# 1) Altyapı (Kafka, PostgreSQL × 9, Redis, Keycloak)
cd docker && docker compose up -d && cd ..

# 2) Servisleri başlat (ayrı terminallerde)
# config-server → eureka_server → gateway_server → 9 iş servisi
cd config-server && mvn spring-boot:run

# 3) Demo verisi (opsiyonel)
./scripts/seed-demo-data.sh

# 4) Web arayüzü
# http://localhost:8080/TelcoX.html
```

Detaylı adımlar, demo kullanıcıları ve sorun giderme için **[docs/DEMO.md](docs/DEMO.md)** dosyasına bakın.

Swagger UI her serviste `/swagger-ui.html` adresindedir (ör. `http://localhost:9002/swagger-ui.html`).

---

## 📚 Dokümantasyon

| Doküman | Açıklama |
|---|---|
| [docs/PROJE_GEREKSINIMLERI.md](docs/PROJE_GEREKSINIMLERI.md) | MVP analiz ve tasarım (33 FR) |
| [docs/DEMO.md](docs/DEMO.md) | TelcoX web arayüzü demo rehberi |
| [docs/event-contracts.md](docs/event-contracts.md) | Kafka event sözleşmeleri |
| [docs/YONETICI_SUNUM_RAPORU.md](docs/YONETICI_SUNUM_RAPORU.md) | Yönetici sunum raporu |
| [k8s/product-catalog/README.md](k8s/product-catalog/README.md) | Kubernetes örnek deployment |
| Servis README'leri | Her servis klasöründe (`billing-service/README.md` vb.) |

---

## 📊 ER Diyagramları

### Genel ER Diyagramı (Unified)

Tüm servislerin tek bir yapı olarak modellendiği genel ER diyagramı — ilk tasarım aşamasında oluşturulmuştur.

![Genel ER Diagram](Genel%20ER%20Diagram.png)

---

### Servis Bazlı ER Diyagramları

Her mikroservisin ER diyagramı ilgili servis klasöründe PNG olarak bulunmaktadır.
Diyagramlar [dbdiagram.io](https://dbdiagram.io) üzerinde **database per service** prensibiyle ayrı ayrı oluşturulmuştur.

| Servis | Diyagram |
|---|---|
| customer-service | [customer-service-ER.png](customer-service/customer-service-ER.png) |
| product-catalog-service | [product-catalog-service-er.png](product-catalog-service/product-catalog-service-er.png) |
| order-service | [order-service-ER.png](order-service/order-service-ER.png) |
| subscription-service | [subscription-service-ER.png](subscription-service/subscription-service-ER.png) |
| billing-service | [Billing_Service-ER.png](billing-service/Billing_Service-ER.png) |
| payment-service | [Payment_Service-ER.png](payment-service/Payment_Service-ER.png) |
| usage-service | [Usage_Service-ER.png](usage-service/Usage_Service-ER.png) |
| notification-service | [notification-service-er.png](notification-service/notification-service-er.png) |
| ticket-service | [ticket-service-er.png](ticket-service/ticket-service-er.png) |

---

## ✅ Proje Durumu

MVP kapsamındaki **33 fonksiyonel gereksinim** tamamlanmıştır.

| Alan | Durum |
|---|---|
| 9 iş mikroservisi + gateway, Eureka, config-server | ✅ |
| Database-per-service + Flyway | ✅ |
| Transactional Outbox + FAILED retry | ✅ |
| Saga orchestration (order-service) | ✅ |
| Kafka event-driven entegrasyon | ✅ |
| JWT güvenlik (gateway + 7 servis) | ✅ |
| Uçtan uca onboarding, fatura, kota senaryoları | ✅ |
| TelcoX web arayüzü (Keycloak + demo verisi) | ✅ |
| GitHub Actions CI | ✅ |
| Docker Compose altyapısı | ✅ |

> **Not:** `order-service` ve `subscription-service` servis seviyesinde JWT henüz eklenmedi; gateway üzerinden erişimde JWT doğrulaması gateway'de yapılır.

---

## 👥 Ekip

| İsim | Sorumlu Servisler |
|---|---|
| Nasrulla Emin | product-catalog-service · notification-service · ticket-service |
| Aymina Çakır | customer-service · order-service · subscription-service |
| Mervenur Küçükkara | billing-service · payment-service · usage-service |

---

<div align="center">

*Turkcell Geleceği Yazanlar Bootcamp — 2026*

</div>
