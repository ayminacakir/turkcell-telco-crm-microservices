# Event Sözleşmeleri (Event Contracts)

Servisler arası Kafka event'lerinin sözleşme dokümanı. Consumer kodlarında bu
dosyaya referans verilir; bir event'in payload'ı değişecekse **önce burası
güncellenir ve ilgili servis sahipleriyle anlaşılır**, sonra kod değişir.

Tüm event'ler ortak zarf alanları taşır: `eventId` (UUID, idempotency anahtarı),
`eventType` (String), ve bir zaman damgası. Tarihler ISO-8601, para alanları
`BigDecimal` + ayrı `currency` (TRY) alanıdır.

Sahiplik: her topic'i **tek bir servis publish eder** (transactional outbox ile);
tüketici sayısı serbesttir. İdempotency: tüm consumer'lar `eventId`'yi
`processed_events` tablosunda takip eder, aynı event ikinci kez işlenmez.

---

## 1. Topic Kataloğu

| Topic | Publisher | Bilinen Consumer'lar | Durum |
|---|---|---|---|
| `customer.registered` | customer-service (Aymina) | notification | ✅ Netleşti |
| `order.created` | order-service (Aymina) | payment | ✅ Netleşti |
| `payment.completed` | payment-service (Mervenur) | order, subscription, notification | ✅ Netleşti (#2.2) |
| `payment.failed` | payment-service (Mervenur) | order, notification | ✅ Netleşti (#2.2) |
| `subscription.activated` | subscription-service (Aymina) | order, billing, notification | ✅ Netleşti |
| `invoice.generated` | billing-service (Mervenur) | notification, payment | ✅ Netleşti |
| `quota.threshold.reached` | usage-service (Mervenur) | notification | ✅ Netleşti (#2.5) — customerId bekleniyor (Açık Soru #6) |
| `quota.exceeded` | usage-service (Mervenur) | notification | ✅ Netleşti (#2.5) — customerId bekleniyor (Açık Soru #6) |
| `usage.aggregated` | usage-service (Mervenur) | billing | ✅ Netleşti |
| `tariff.created` | product-catalog-service (Nasrulla) | — | ✅ Netleşti |
| `tariff.price.changed` | product-catalog-service (Nasrulla) | — | ✅ Netleşti |
| `ticket.opened` | ticket-service (Nasrulla) | notification | ✅ Netleşti |
| `ticket.resolved` | ticket-service (Nasrulla) | notification | ✅ Netleşti |
| `ticket.sla.breached` | ticket-service (Nasrulla) | — | ✅ Netleşti |
| `notification.dispatched` | notification-service (Nasrulla) | — | ✅ Netleşti |

---

## 2. Payload Tanımları

### 2.1 customer.registered — `CustomerRegistered`

```json
{ "eventId": "uuid", "eventType": "CustomerRegistered",
  "customerId": "uuid", "fullName": "string", "email": "string",
  "msisdn": "string", "registeredAt": "ISO-8601" }
```

### 2.2 payment.completed / payment.failed — `PaymentCompleted` / `PaymentFailed`

```json
{ "eventId": "uuid", "eventType": "PaymentCompleted",
  "paymentId": "uuid", "orderId": "uuid", "customerId": "uuid",
  "amount": "decimal", "currency": "TRY", "status": "string",
  "paidAt": "ISO-8601" }
```

`payment.failed` ek olarak `reason` (String) ve `failedAt` taşır, `paidAt` taşımaz.

### 2.3 subscription.activated — `SubscriptionActivated`

```json
{ "eventId": "uuid", "eventType": "SubscriptionActivated",
  "subscriptionId": "uuid", "orderId": "uuid", "customerId": "uuid",
  "tariffCode": "string", "msisdn": "string", "activatedAt": "ISO-8601" }
```

### 2.4 invoice.generated — `InvoiceGenerated`

```json
{ "eventId": "uuid", "eventType": "InvoiceGenerated",
  "invoiceId": "uuid", "customerId": "uuid", "subscriptionId": "uuid",
  "amount": "decimal", "currency": "TRY", "dueDate": "yyyy-MM-dd",
  "generatedAt": "ISO-8601" }
```

### 2.5 quota.threshold.reached / quota.exceeded — `QuotaThresholdReached` / `QuotaExceeded`

Usage-service'in **gerçek** payload'ı (origin/merve `054c48f`, `usage_service.event.QuotaThresholdEvent`
sınıfından doğrulandı). İki topic de aynı yapıyı kullanır; `eventType` alanı ayırt eder:

```json
{ "eventId": "uuid", "eventType": "QuotaThresholdReached",
  "subscriptionId": "uuid", "type": "VOICE|SMS|DATA",
  "threshold": "PERCENT_80|PERCENT_100", "remaining": 0 }
```

> ⚠️ **Eksik alan — Açık Soru #6:** Event'te `customerId` yok; notification bildirimi kime
> göndereceğini bilemez ve bu event'leri (loglayıp) atlar. `Quota` entity'sinde `customer_id`
> alanı zaten mevcut — usage-service'in event'e `customerId` eklemesi bekleniyor. Alan
> eklendiği anda notification tarafı kod değişikliği gerektirmeden çalışır.

### 2.6 tariff.created — `TariffCreated`

```json
{ "eventId": "uuid", "eventType": "TariffCreated",
  "tariffId": "uuid", "code": "string", "name": "string",
  "type": "POSTPAID|PREPAID|HYBRID", "monthlyFee": "decimal",
  "minutesIncluded": 0, "smsIncluded": 0, "dataMbIncluded": 0,
  "occurredAt": "ISO-8601" }
```

`tariff.price.changed` (`TariffPriceChanged`): `tariffId`, `code`, `oldMonthlyFee`,
`newMonthlyFee`, `occurredAt`.

### 2.7 ticket.opened / ticket.resolved / ticket.sla.breached

```json
{ "eventId": "uuid", "eventType": "TicketOpened",
  "ticketId": "uuid", "customerId": "uuid", "category": "string",
  "priority": "LOW|MEDIUM|HIGH|CRITICAL", "slaDueAt": "ISO-8601",
  "occurredAt": "ISO-8601" }
```

`TicketResolved`: `ticketId`, `customerId`, `occurredAt`.
`SlaBreached`: `ticketId`, `customerId`, `priority`, `slaDueAt`, `occurredAt`.

### 2.8 notification.dispatched — `NotificationDispatched`

```json
{ "eventId": "uuid", "eventType": "NotificationDispatched",
  "notificationId": "uuid", "userId": "uuid", "templateCode": "string",
  "channel": "SMS|EMAIL|PUSH", "occurredAt": "ISO-8601" }
```

---

## 3. Notification Template Eşlemesi

| Tetikleyen event | templateCode | Kanal | Placeholder'lar |
|---|---|---|---|
| CustomerRegistered | `CUSTOMER_WELCOME` | SMS | fullName |
| SubscriptionActivated | `WELCOME_SMS` | SMS | tariffCode, msisdn |
| InvoiceGenerated | `INVOICE_GENERATED` | EMAIL | amount, currency, dueDate |
| PaymentCompleted | `PAYMENT_RECEIVED` | SMS | amount, currency, orderId |
| PaymentFailed | `PAYMENT_FAILED` | SMS | amount, currency, reason |
| QuotaThresholdReached | `QUOTA_WARNING_80` | SMS | thresholdPercentage |
| QuotaExceeded | `QUOTA_EXCEEDED` | SMS | — |
| TicketOpened | `TICKET_OPENED` | SMS | category, priority |
| TicketResolved | `TICKET_RESOLVED` | SMS | — |

---

## Açık Sorular

1. ~~payment.completed payload formatı~~ → Netleşti (#2.2).
2. ~~`quota.threshold.reached` payload formatı~~ → Netleşti (#2.5): gerçek format
   usage-service kodundan doğrulandı; `QuotaThresholdReachedEvent` DTO'su güncellendi.
3. Eşik değerleri usage-service'te sabit: %80'de bir kez `PERCENT_80`, %100'de
   `PERCENT_100` üretilir (UsageServiceImpl). Kota tipi başına (VOICE/SMS/DATA)
   ayrı event gelir — notification şu an tip ayrımı yapmadan aynı şablonu kullanıyor,
   ileride tipe göre şablon zenginleştirilebilir.
4. `invoice.generated`'ı payment-service auto-pay için tüketiyor (Mervenur'un raporu
   #4.7 doğruladı) → kapandı.
5. ~~`quota.exceeded` ayrı bir event tipi mi?~~ → Netleşti: aynı `QuotaThresholdEvent`
   yapısı, `eventType=QuotaExceeded` ve `threshold=PERCENT_100` ile ayrışıyor.
6. **`customerId` alanı eksik (AKTİF):** quota event'lerinde `customerId` yok;
   notification bildirimi adresleyemiyor ve bu event'leri atlıyor. `Quota` entity'sinde
   `customer_id` mevcut — **Mervenur'dan istek:** `QuotaThresholdEvent`'e `customerId`
   alanını eklemesi. Eklendiğinde notification tarafı otomatik çalışır.
