# 📡 Turkcell Telco CRM Microservices

> Turkcell "Geleceği Yazanlar" bootcamp kapsamında geliştirilen, hayali bir GSM operatörü **(TelcoX)** için mikroservis tabanlı CRM platformu.

---

## 📋 İçindekiler

- [Proje Hakkında](#proje-hakkında)
- [Mimari](#mimari)
- [Mikroservisler](#mikroservisler)
- [Tech Stack](#tech-stack)
- [Kurulum](#kurulum)
- [ER Diyagramları](#er-diyagramları)
- [Proje Durumu](#proje-durumu)
- [Yapılacaklar](#yapılacaklar)

---

## 📖 Proje Hakkında

TelcoX CRM, bir GSM operatörünün müşteri yönetiminden faturaya, ödemeden bildirimlere kadar tüm süreçlerini kapsayan **mikroservis tabanlı** bir CRM platformudur.

Proje 3 temel MVP senaryosu üzerine kurgulanmıştır:

1. **Yeni Abone Onboarding** → Müşteri kaydı → KYC → Sipariş → Ödeme → Aktivasyon → SMS bildirimi
2. **Aylık Fatura** → Bill-run → Kullanım toplamı → Fatura oluşturma → Bildirim → Ödeme
3. **Kota Aşımı** → CDR → Kota takibi → %80'de uyarı SMS → %100'de ek paket bildirimi

---

## 🏗️ Mimari

Her mikroservis **bağımsız bir Spring Boot uygulaması** olarak çalışır ve **kendi veritabanına** sahiptir. Servisler arası iletişim event-driven (Kafka) olarak planlanmıştır. Servisler arası doğrudan veritabanı FK kullanılmaz.

```
turkcell-telco-crm-microservices/   ← Multi-module Maven root
├── customer-service/               ← Port 9002 | customer_db
├── product-catalog-service/        ← Port 9003 | product_catalog_db
├── order-service/                  ← Port 9004 | order_db
├── subscription-service/           ← Port 9005 | subscription_db
├── usage-service/                  ← Port 9006 | usage_db
├── billing-service/                ← Port 9007 | billing_db
├── payment-service/                ← Port 9008 | payment_db
├── notification-service/           ← Port 9009 | notification_db
├── ticket-service/                 ← Port 9010 | ticket_db
└── pom.xml                         ← Root pom (parent)
```

---

## 🧩 Mikroservisler

| Servis | Port | Veritabanı | Sorumlu | Açıklama |
|---|---|---|---|---|
| customer-service | 9002 | customer_db | Aymina | Müşteri kaydı, adres, KYC belgesi |
| product-catalog-service | 9003 | product_catalog_db | Sen | Tarife ve ek paket kataloğu |
| order-service | 9004 | order_db | Aymina | Sipariş ve Saga yönetimi |
| subscription-service | 9005 | subscription_db | Aymina | Abonelik, MSISDN, SIM kart |
| usage-service | 9006 | usage_db | Mervenur | Kota ve kullanım (CDR) kayıtları |
| billing-service | 9007 | billing_db | Mervenur | Fatura üretimi ve döngüsü |
| payment-service | 9008 | payment_db | Mervenur | Ödeme ve ödeme girişimleri |
| notification-service | 9009 | notification_db | Sen | SMS, e-posta, push bildirim |
| ticket-service | 9010 | ticket_db | Sen | Müşteri talep ve şikayetleri |

---

## 🛠️ Tech Stack

| Kategori | Teknoloji |
|---|---|
| Dil | Java 21 |
| Framework | Spring Boot 3 |
| ORM | Spring Data JPA / Hibernate |
| Veritabanı | PostgreSQL 15 |
| Build | Maven (Multi-module) |
| Container | Docker |
| Mesajlaşma | Apache Kafka *(planlanan)* |
| Service Discovery | Eureka *(planlanan)* |
| API Gateway | Spring Cloud Gateway *(planlanan)* |

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

### 4. Servisi Çalıştır

```bash
# Tek bir servisi çalıştırmak için
cd customer-service
mvn spring-boot:run
```

---

## 📊 ER Diyagramları

Her mikroservisin ER diyagramı ilgili servis klasöründe PNG olarak bulunmaktadır. Diyagramlar [dbdiagram.io](https://dbdiagram.io) üzerinde oluşturulmuştur.

| Servis | Diyagram | Durum |
|---|---|---|
| customer-service | [customer-service-ER.png](customer-service/customer-service-ER.png) | ✅ |
| product-catalog-service | [product-catalog-service-er.png](product-catalog-service/product-catalog-service-er.png) | ✅ |
| order-service | [order-service-ER.png](order-service/order-service-ER.png) | ✅ |
| subscription-service | [subscription-service-ER.png](subscription-service/subscription-service-ER.png) | ✅ |
| billing-service | [Billing_Service-ER.png](billing-service/Billing_Service-ER.png) | ✅ |
| payment-service | [Payment_Service-ER.png](payment-service/Payment_Service-ER.png) | ✅ |
| usage-service | [Usage_Service-ER.png](usage-service/Usage_Service-ER.png) | ✅ |
| notification-service | [notification-service-er.png](notification-service/notification-service-er.png) | ✅ |
| ticket-service | [ticket-service-er.png](ticket-service/ticket-service-er.png) | ✅ |

---

## ✅ Proje Durumu

### Tamamlanan

- [x] Multi-module Maven yapısı (root pom + child modüller)
- [x] 9 servisin Spring Boot proje iskeletleri
- [x] Database per service prensibi (her servis ayrı DB)
- [x] 9 servisin ER diyagramları (dbdiagram.io → PNG)
- [x] Entity ve Enum sınıfları (tüm servisler)
- [x] application.yaml konfigürasyonları (tüm servisler)

---

## 📌 Yapılacaklar

### Hafta 2 — Bireysel Ödev
- [ ] Her servise `GET /hello` endpoint'i eklemek (HelloController)

### Hafta 3 — Planlanan
- [ ] Repository katmanı (JpaRepository interface'leri)
- [ ] Service katmanı (iş mantığı)
- [ ] Controller katmanı (REST API endpoint'leri)
- [ ] DTO sınıfları (Request / Response)
- [ ] Global exception handling

### İlerleyen Haftalar
- [ ] Eureka Discovery Server kurulumu
- [ ] Spring Cloud Config Server
- [ ] API Gateway
- [ ] Apache Kafka event'leri (servisler arası iletişim)
- [ ] Docker Compose (tüm servisleri tek komutla ayağa kaldırmak)
- [ ] Unit ve integration testleri

---

## 👥 Ekip

| İsim | Servisler |
|---|---|
| Sen | product-catalog-service, notification-service, ticket-service |
| Aymina | customer-service, order-service, subscription-service |
| Mervenur | billing-service, payment-service, usage-service |

---

*Turkcell Geleceği Yazanlar Bootcamp — 2026*
