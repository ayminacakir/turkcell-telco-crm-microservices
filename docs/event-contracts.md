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
| `quota.threshold.reached` | usage-service (Mervenur) | notification | ⚠️ TASLAK (Açık Soru #2) |
| `quota.exceeded` | usage-service (Mervenur) | notification | ⚠️ TASLAK (Açık Soru #5) |
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

### 2.5 quota.threshold.reached / quota.exceeded — ⚠️ TASLAK

Notification tarafındaki mevcut tahmini format (`QuotaThresholdReachedEvent`):

```json
{ "eventId": "uuid", "eventType": "QuotaThresholdReached",
  "customerId": "uuid", "subscriptionId": "uuid", "msisdn": "string",
  "thresholdPercentage": 80, "occurredAt": "ISO-8601" }
```

> Usage-service'in gerçek payload'ı ile **doğrulanmadı** — bkz. Açık Sorular #2 ve #5.

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
2. **`quota.threshold.reached` payload formatı** — usage-service (Mervenur) gerçekte
   hangi alanları publish ediyor? `customerId` mi `userId` mı? `thresholdPercentage`
   alan adı doğru mu? Netleşince `QuotaThresholdReachedEvent` ve bu doküman güncellenmeli.
3. Eşik değerleri (%80/%100) usage-service'te mi sabit, config'den mi geliyor?
   Farklı eşikler gelirse notification tarafında template seçimi eşiğe göre yapılmalı
   (şu an %80 → `QUOTA_WARNING_80` sabit eşlemesi var).
4. `invoice.generated`'ı payment-service'in auto-pay senaryosu için tüketmesi
   planlanıyor (doküman 8.7) — Mervenur tarafında bu consumer var mı?
5. **`quota.exceeded` ayrı bir event tipi mi**, yoksa `QuotaThresholdReached(100)` mü?
   Şu an notification her iki topic'i de aynı DTO ile parse ediyor.
