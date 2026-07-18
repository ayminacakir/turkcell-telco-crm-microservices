# Kabul Senaryoları — Uçtan Uca Test Rehberi (Bölüm 14)

Bu rehber, `docs/PROJE_GEREKSINIMLERI.md` Bölüm 14'teki 3 kabul senaryosunu
**terminalden uçtan uca** çalıştırmak içindir. Her adımda çalıştırılacak komut +
beklenen sonuç + doğrulama sorgusu vardır. Olaylar asenkron (Kafka) aktığı için
adımlar arasında kısa `sleep` vardır.

---

## 0) Ön Koşullar (bir kez)

```bash
cd ~/turkcell-telco-crm-microservices

# 1) Altyapı ayakta mı (docker: db'ler + kafka + keycloak + redis)
cd docker && docker compose up -d && cd ..

# 2) Tüm servisleri başlat + demo veriyi yükle
./scripts/stop-all.sh
./scripts/run-all.sh          # ~1-2 dk; ./scripts/status.sh ile 12/12 bekle
./scripts/seed-demo-data.sh   # abonelik + kota + FREE SIM'ler + faturalar

# 3) jq kurulu mu (JSON parse için) — yoksa: brew install jq
which jq || echo "jq gerekli: brew install jq"
```

### Yönetici (ops) token'ı al — çoğu admin ucu için gerekli
```bash
TOKEN=$(curl -s -X POST http://localhost:8085/realms/telco-crm/protocol/openid-connect/token \
  -d grant_type=password -d client_id=telcox-web -d username=ops -d password=telcox123 \
  | jq -r .access_token)
echo "${TOKEN:0:20}...  (dolu olmalı)"

# kısayol: tüm API çağrıları gateway (8080) üzerinden, token ile
api() { curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" "$@"; }
# psql kısayolu (event ile üretilen id'leri okumak/doğrulamak için)
q() { docker exec -i "$1" psql -U postgres -d "$2" -tAc "$3"; }
```

> Not: Bu rehber ADMIN (`ops`) token'ıyla ilerler; ADMIN her ucu çağırabilir.
> Gerçek RBAC'i görmek isterseniz `elif.aydin` (USER) token'ıyla admin uçları 403 döner.

---

## 14.1 — Yeni Abone Onboarding

**Akış:** müşteri kaydı → KYC belge + onay → sipariş → `order.created` → payment
oluşur → ödeme işlenir → `payment.completed` → subscription aktive + MSISDN → welcome SMS.

```bash
# 1) Müşteri kaydı (POST /customers)
CUST=$(api -X POST http://localhost:8080/api/v1/customers -d '{
  "type":"INDIVIDUAL","firstName":"Deniz","lastName":"Yılmaz",
  "identityNumber":"12345678901","dateOfBirth":"1995-05-10"
}' | jq -r .id)
echo "Yeni müşteri: $CUST"     # bir UUID basmalı

# 2) KYC belgesi yükle
api -X POST http://localhost:8080/api/v1/customers/$CUST/documents \
  -d '{"type":"ID_CARD","fileRef":"s3://kyc/deniz-id.png"}' | jq .

# 3) KYC onayla (ADMIN)  -> status APPROVED olmalı
api -X POST http://localhost:8080/api/v1/customers/$CUST/kyc/approve | jq '{id,status}'

# 4) Postpaid tarife siparişi (POST /orders)  -> order.created yayınlanır
ORDER=$(api -X POST http://localhost:8080/api/v1/orders -d "{
  \"customerId\":\"$CUST\",
  \"items\":[{\"productCode\":\"SUPER_POSTPAID_20GB\",\"productType\":\"TARIFF\",
             \"productName\":\"Süper Postpaid 20GB\",\"quantity\":1,\"unitPrice\":349.90}]
}" | jq -r .id)
echo "Sipariş: $ORDER"
sleep 5   # payment-service order.created'ı tüketip PENDING payment oluştursun

# 5) Oluşan payment'ı bul (order_id ile) ve işle
PAY=$(q payment-db payment_db "SELECT id FROM payment_service.payments WHERE order_id='$ORDER' LIMIT 1")
echo "Payment: $PAY"          # dolu bir UUID olmalı (boşsa 3-4 sn daha bekleyin)
api -X POST http://localhost:8080/api/v1/payments/$PAY/process | jq '{id,status}'   # COMPLETED
sleep 6   # subscription-service payment.completed'ı tüketip aktive etsin
```

**Beklenen sonuç & doğrulama:**
```bash
# a) Abonelik otomatik oluştu + MSISDN atandı (ACTIVE)
api http://localhost:8080/api/v1/subscriptions/customers/$CUST | jq '.[]|{msisdn,tariffCode,status}'
#  -> {"msisdn":"0536000...","tariffCode":"SUPER_POSTPAID_20GB","status":"ACTIVE"}

# b) MSISDN havuzunda o numara ALLOCATED oldu
q subscription-db subscription_db "SELECT msisdn,status FROM msisdn_pool WHERE status='ALLOCATED'"

# c) Welcome / subscription bildirimi düştü (mock log = notifications tablosu)
q notification-db notification_db \
  "SELECT template_code,channel,status FROM notification_service.notifications WHERE user_id='$CUST'"
#  -> WELCOME_SMS / CUSTOMER_WELCOME / SUBSCRIPTION_ACTIVATED benzeri SENT kaydı
```
✅ **Geçti sayılır:** abonelik `ACTIVE` + MSISDN atanmış + bildirim `SENT`.

---

## 14.2 — Aylık Fatura

**Akış:** bill-run tetikle → aktif aboneler için usage agregasyonu + invoice + PDF →
`invoice.generated` → notification e-posta → fatura ödenince `payment.completed`.

```bash
# 1) Bill-run tetikle (ADMIN)
api -X POST http://localhost:8080/api/v1/billing/runs ; echo
sleep 6   # faturalar üretilsin + invoice.generated yayılsın

# 2) Elif'in faturaları (seed'den 3 + bill-run'dan yenisi olabilir)
api http://localhost:8080/api/v1/invoices/customer/11111111-0000-4000-8000-000000000001 \
  | jq '.[]|{periodStart,grandTotal,status}'

# 3) Bir faturanın PDF'i gerçekten üretiliyor mu (200 + application/pdf)
INV=$(api http://localhost:8080/api/v1/invoices/customer/11111111-0000-4000-8000-000000000001 \
      | jq -r '.[0].id')
curl -s -o /tmp/fatura.pdf -w "PDF HTTP:%{http_code} tip:%{content_type} boyut:%{size_download}\n" \
  -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/invoices/$INV/pdf
#  -> PDF HTTP:200 tip:application/pdf boyut:>0
```

**Beklenen sonuç & doğrulama:**
```bash
# a) invoice.generated bildirimi (EMAIL) düştü
q notification-db notification_db \
  "SELECT template_code,channel,status FROM notification_service.notifications
   WHERE template_code='INVOICE_GENERATED' ORDER BY sent_at DESC LIMIT 3"
#  -> INVOICE_GENERATED / EMAIL / SENT

# b) Faturayı öde: PENDING faturaya payment oluştur + işle
PINV=$(api http://localhost:8080/api/v1/invoices/customer/11111111-0000-4000-8000-000000000001 \
       | jq -r '[.[]|select(.status=="PENDING")][0].id')
PAMT=$(api http://localhost:8080/api/v1/invoices/$PINV | jq -r .grandTotal)
PPAY=$(api -X POST http://localhost:8080/api/v1/payments -d "{
  \"invoiceId\":\"$PINV\",\"amount\":$PAMT,\"method\":\"CREDIT_CARD\"}" | jq -r .id)
api -X POST http://localhost:8080/api/v1/payments/$PPAY/process | jq '{id,status}'   # COMPLETED
sleep 5
# c) Fatura PAID'e döndü mü
api http://localhost:8080/api/v1/invoices/$PINV | jq '{status}'   # -> "PAID"
```
✅ **Geçti sayılır:** bill-run fatura üretti + PDF 200 + `INVOICE_GENERATED` e-posta SENT +
ödeme sonrası fatura `PAID`.

---

## 14.3 — Kota Aşımı (%80 → %100 → overage)

**Akış:** CDR usage event'leri (POST /usage/records) → usage-service kotayı azaltır →
%80'de `quota.threshold.reached` → notification SMS → %100'de `quota.exceeded` →
notification SMS + öneri → aşım `usage.aggregated` ile billing'e overage gider.

> Elif'in (SUB1) dakika kotası seed'de: **312 / 500** (%37 kullanılmış). Aşağıda dakika
> kullanımı basıp önce %80'i sonra %100'ü aşacağız.

```bash
SUB1=22222222-0000-4000-8000-000000000001

# 0) Başlangıç kotası
api http://localhost:8080/api/v1/usage/subscriptions/$SUB1/quota | jq '{minutesRemaining,minutesTotal}'
#  -> {"minutesRemaining":312,"minutesTotal":500}

# 1) 250 dk konuşma  -> kalan 62 (=%87.6 kullanım) -> %80 EŞİĞİ aşılır
api -X POST http://localhost:8080/api/v1/usage/records -d "{
  \"subscriptionId\":\"$SUB1\",\"type\":\"VOICE\",\"quantity\":250,\"cdrRef\":\"CDR-TEST-80\"}" | jq '{type,quantity}'
sleep 4

# 2) 100 dk daha  -> kalan 0 + 38 dk overage -> %100 AŞILDI
api -X POST http://localhost:8080/api/v1/usage/records -d "{
  \"subscriptionId\":\"$SUB1\",\"type\":\"VOICE\",\"quantity\":100,\"cdrRef\":\"CDR-TEST-100\"}" | jq '{type,quantity}'
sleep 5
```

**Beklenen sonuç & doğrulama:**
```bash
# a) Kota tükendi
api http://localhost:8080/api/v1/usage/subscriptions/$SUB1/quota | jq '{minutesRemaining}'   # -> 0

# b) %80 ve %100 bildirimleri düştü (SMS) — customerId sayesinde doğru kişiye
q notification-db notification_db \
  "SELECT template_code,channel,status FROM notification_service.notifications
   WHERE user_id='11111111-0000-4000-8000-000000000001'
     AND template_code IN ('QUOTA_WARNING_80','QUOTA_EXCEEDED') ORDER BY sent_at DESC"
#  -> QUOTA_WARNING_80 / SMS / SENT  ve  QUOTA_EXCEEDED / SMS / SENT

# c) Aşım billing'e gitti (usage.aggregated -> overage). Outbox'ta event üretildi mi:
q usage-db usage_db \
  "SELECT event_type,status FROM usage_service.outbox_events
   WHERE event_type IN ('QuotaThresholdReached','QuotaExceeded','UsageAggregated')
   ORDER BY created_at DESC LIMIT 5"
#  -> QuotaExceeded, QuotaThresholdReached, UsageAggregated kayıtları (PUBLISHED)

# d) Kota satırında overage sayacı arttı
q usage-db usage_db "SELECT overage_voice_minutes FROM usage_service.quotas WHERE subscription_id='$SUB1'"
#  -> 38 (veya >0)
```
✅ **Geçti sayılır:** kota 0 + `QUOTA_WARNING_80` ve `QUOTA_EXCEEDED` SMS SENT +
overage kaydı billing'e giden `UsageAggregated` event'i.

---

## Sorun Giderme

| Belirti | Sebep / Çözüm |
|---|---|
| `Payment: ` boş | Kafka gecikmesi → 3-5 sn daha bekleyip 5. adımı tekrar çalıştırın (`q ...`) |
| Abonelik `ACTIVE` olmadı | FREE MSISDN/SIM kalmamış olabilir → `seed-demo-data.sh` FREE SIM'leri ekler; tekrar seed'leyin |
| Bildirim tablosu boş | notification-service ayakta mı (`./scripts/status.sh`) + Kafka çalışıyor mu |
| `401/403` | Token süresi doldu (1 saat) → 0. adımdaki token komutunu tekrar çalıştırın |
| PDF 500 | billing-service ayakta değil ya da fatura yok → önce bill-run |
| Kota değişmiyor | usage-service ayakta değil ya da aktif dönem kotası yok → seed'i tekrar çalıştırın |

> Alternatif: **Müşteri portalı** (`http://localhost:8080/TelcoX.html` → `elif.aydin`)
> ile 14.2 (fatura görüntüle/öde) ve 14.3 (kullanım/kota) müşteri gözünden de doğrulanabilir.
