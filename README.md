<div align="center">

# 📡 Turkcell Telco CRM Microservices

**Turkcell "Geleceği Yazanlar" Bootcamp — 2026**

*Hayali bir GSM operatörü TelcoX için mikroservis tabanlı CRM platformu*

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-green?style=flat-square&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?style=flat-square&logo=postgresql)
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
- [ER Diyagramları](#-er-diyagramları)
- [Proje Durumu](#-proje-durumu)
- [Yapılacaklar](#-yapılacaklar)
- [Ekip](#-ekip)

---

## 📖 Proje Hakkında

TelcoX CRM, bir GSM operatörünün tüm iş süreçlerini yöneten **mikroservis tabanlı** kurumsal bir CRM platformudur. Müşteri kaydından faturaya, ödemeden bildirimlere kadar uçtan uca akışı kapsar.

**Temel prensipler:**
- 🔒 **Database per service** — Her mikroservis yalnızca kendi veritabanına erişir
- 🔗 **Loose coupling** — Servisler arası doğrudan FK kullanılmaz; ID referansı tutulur
- 📨 **Event-driven** — Servisler arası iletişim Kafka üzerinden planlanmıştır
- 🏗️ **Multi-module Maven** — Tüm servisler tek repo, ortak parent pom

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
└── 📄 pom.xml                    ← Root parent pom
```

---

## 🧩 Mikroservisler

| # | Servis | Port | Veritabanı | Sorumlu | Açıklama |
|---|---|---|---|---|---|
| 1 | customer-service | 9002 | customer_db | Aymina | Müşteri kaydı, adres, KYC belgesi |
| 2 | product-catalog-service | 9003 | product_catalog_db | Sen | Tarife, ek paket kataloğu |
| 3 | order-service | 9004 | order_db | Aymina | Sipariş yönetimi, Saga pattern |
| 4 | subscription-service | 9005 | subscription_db | Aymina | Abonelik, MSISDN havuzu, SIM kart |
| 5 | usage-service | 9006 | usage_db | Mervenur | Kota takibi, CDR kullanım kayıtları |
| 6 | billing-service | 9007 | billing_db | Mervenur | Fatura üretimi, fatura döngüsü |
| 7 | payment-service | 9008 | payment_db | Mervenur | Ödeme, ödeme girişimleri |
| 8 | notification-service | 9009 | notification_db | Sen | SMS, e-posta, push bildirimleri |
| 9 | ticket-service | 9010 | ticket_db | Sen | Müşteri talep ve şikayetleri |

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
| Framework | Spring Boot 3 | ✅ |
| ORM | Spring Data JPA / Hibernate | ✅ |
| Veritabanı | PostgreSQL 15 | ✅ |
| Build | Maven (Multi-module) | ✅ |
| Container | Docker | ✅ |
| Mesajlaşma | Apache Kafka | 🔜 Planlanan |
| Service Discovery | Eureka Server | 🔜 Planlanan |
| API Gateway | Spring Cloud Gateway | 🔜 Planlanan |
| Config | Spring Cloud Config Server | 🔜 Planlanan |

---

## 🚀 Kurulum

### Gereksinimler

- Java 21+
- Maven 3.9+
- Docker Desktop

### 1. Repoyu Klonla

```bash
git clone https://github.com/ayminacakir/turkcell-telco-crm-microservices
cd turkcell-telco-crm-microservices
```

### 2. PostgreSQL'i Docker ile Başlat

```bash
docker run -d \
  --name telco-postgres \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=admin \
  -p 5432:5432 \
  postgres:15
```

### 3. Veritabanlarını Oluştur

```bash
docker exec -it telco-postgres psql -U admin -c "CREATE DATABASE customer_db;"
docker exec -it telco-postgres psql -U admin -c "CREATE DATABASE product_catalog_db;"
docker exec -it telco-postgres psql -U admin -c "CREATE DATABASE order_db;"
docker exec -it telco-postgres psql -U admin -c "CREATE DATABASE subscription_db;"
docker exec -it telco-postgres psql -U admin -c "CREATE DATABASE usage_db;"
docker exec -it telco-postgres psql -U admin -c "CREATE DATABASE billing_db;"
docker exec -it telco-postgres psql -U admin -c "CREATE DATABASE payment_db;"
docker exec -it telco-postgres psql -U admin -c "CREATE DATABASE notification_db;"
docker exec -it telco-postgres psql -U admin -c "CREATE DATABASE ticket_db;"
```

### 4. Tek Servis Çalıştır

```bash
cd customer-service
mvn spring-boot:run
```

---

## 📊 ER Diyagramları

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

| # | Görev | Durum |
|---|---|---|
| 1 | Multi-module Maven yapısı | ✅ Tamamlandı |
| 2 | 9 servisin Spring Boot iskeletleri | ✅ Tamamlandı |
| 3 | Database per service prensibi | ✅ Tamamlandı |
| 4 | 9 servisin ER diyagramları (PNG) | ✅ Tamamlandı |
| 5 | Tüm servislerin Entity ve Enum sınıfları | ✅ Tamamlandı |
| 6 | Tüm servislerin application.yaml konfigürasyonları | ✅ Tamamlandı |
| 7 | Her servise GET /hello endpoint'i | 🔄 Devam Ediyor |
| 8 | Repository katmanı | 🔜 Planlanan |
| 9 | Service katmanı | 🔜 Planlanan |
| 10 | Controller katmanı (REST API) | 🔜 Planlanan |
| 11 | DTO sınıfları | 🔜 Planlanan |
| 12 | Global exception handling | 🔜 Planlanan |
| 13 | Eureka Discovery Server | 🔜 Planlanan |
| 14 | API Gateway | 🔜 Planlanan |
| 15 | Kafka event'leri | 🔜 Planlanan |
| 16 | Docker Compose | 🔜 Planlanan |

---

## 📌 Yapılacaklar

### 🔄 Hafta 2 — Bireysel Ödev
- [ ] Her servise `GET /hello` endpoint'i (HelloController)

### 📅 Hafta 3
- [ ] Repository katmanı (JpaRepository interface'leri)
- [ ] Service katmanı (iş mantığı)
- [ ] Controller katmanı (REST API endpoint'leri)
- [ ] DTO sınıfları (Request / Response)
- [ ] Global exception handling (`@ControllerAdvice`)

### 📅 İlerleyen Haftalar
- [ ] Eureka Discovery Server
- [ ] Spring Cloud Config Server
- [ ] API Gateway (Spring Cloud Gateway)
- [ ] Apache Kafka — servisler arası event'ler
- [ ] Docker Compose
- [ ] Unit & integration testleri

---

## 👥 Ekip

| İsim | Sorumlu Servisler |
|---|---|
| Nasrulla | product-catalog-service · notification-service · ticket-service |
| Aymina | customer-service · order-service · subscription-service |
| Mervenur | billing-service · payment-service · usage-service |

---

<div align="center">

*Turkcell Geleceği Yazanlar Bootcamp — 2026*

</div>
