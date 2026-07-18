# TelcoX Web Arayüzü — Demo Çalıştırma Rehberi

Arayüz gateway'in içinden servis edilir (tek origin → CORS derdi yok) ve
**gerçek servislere** bağlıdır: giriş Keycloak'tan gerçek JWT alır, tüm ekranlar
canlı API verisi gösterir.

## 1) Altyapıyı başlat (Keycloak realm otomatik import edilir)

```bash
cd docker && docker compose up -d && cd ..
```

> Keycloak'ı daha önce başlattıysanız realm import'un çalışması için bir kez:
> `docker compose up -d --force-recreate keycloak`

## 2) Servisleri başlat

Sırasıyla (her biri ayrı terminal): config-server → eureka_server → gateway_server →
diğer 9 servis (`mvn spring-boot:run`).

## 3) Demo verisini yükle

```bash
chmod +x scripts/seed-demo-data.sh
./scripts/seed-demo-data.sh
```

Sabit kimliklerle tutarlı veri basar: Elif Aydın'ın aboneliği, %82 dolu internet
kotası, 3 fatura (1 bekleyen), ödemeler, bildirim geçmişi ve 2 destek talebi.

## 4) Arayüzü aç

**http://localhost:8080/TelcoX.html**

| Rol | Kullanıcı | Şifre | Açılan ekran |
|---|---|---|---|
| Müşteri | `elif.aydin` | `telcox123` | Tarife + kota çubukları, faturalar, destek talepleri (yeni talep açma), bildirim geçmişi, opt-in/opt-out |
| Yönetici | `ops` | `telcox123` | 10 servisin canlı sağlık panosu, tarife CRUD + fiyat/versiyon, ticket işleme/çözme, bildirim gönderme, bill-run, müşteri + KYC |

## Nasıl çalışıyor?

- Login sayfası Keycloak `telco-crm` realm'inden ROPC ile token alır (`telcox-web` public client)
- Sayfalar `gateway_server/src/main/resources/static/` içindedir; API çağrıları aynı origin'den
  `/api/v1/**` route'larına gider, `Authorization: Bearer` başlığı gateway'den servislere iletilir
- `ops` kullanıcısı ADMIN rolündedir → `@PreAuthorize(ADMIN)` endpoint'leri (tarife oluşturma,
  bill-run, KYC onayı…) çalışır; `elif.aydin` USER'dır → admin işlemleri 403 alır (canlı RBAC demosu)
- Sağlık panosunda customer/order/subscription/usage/billing/payment "ERİŞİLEMEDİ" görünüyorsa
  sebep CORS'tur (o servislerde CORS konfigürasyonu yok) — servis çalışıyordur, gateway üzerinden
  API'leri yine de çalışır

## Sorun giderme

| Belirti | Sebep / Çözüm |
|---|---|
| Login: "Keycloak'a ulaşılamadı" | `docker ps` ile keycloak ayakta mı bakın; ilk açılışı ~30 sn sürer |
| Login: 401 | Realm import olmamış → `docker compose up -d --force-recreate keycloak` |
| Ekranlar boş | Seed script'i çalıştırılmamış (adım 3) |
| API 401/403 | Token süresi doldu (1 saat) → çıkış yapıp tekrar girin |


## Arayüz ekranları (zengin sürüm)

**Operasyon Konsolu** (`ops` girişi) — sol menülü SPA, dark mode toggle:
- **Dashboard**: canlı istatistikler, servis durum şeridi, gelir grafiği, alarm özeti, realtime aktivite akışı
- **Ürünler**: gerçek `/api/v1/tariffs` — kod, tip, segment, versiyon, ücret
- **Ticket Center**: gerçek `/api/v1/tickets` — satırdan "İşleme Al" / "Çöz" (gerçek PATCH/POST)
- **Faturalama**: gerçek faturalar + bill-run
- **Monitoring**: Kubernetes/HPA pod korelasyonu, servis kartları, ölçekleme olayları
- **Logs / Alarm Merkezi / Incident**: canlı log akışı, alarm acknowledge/restart, incident kaydı
- **Servis detay drawer'ı**: her servise tıkla → runbook (olası sorun/çözüm) + operasyon (restart/scale)
- **AI Asistan**: "payment neden yavaş?" gibi sorulara servis durumundan cevap

> Servis UP/DOWN durumu **gerçektir** (gateway `/ops/health` sunucu tarafında tüm servislerin
> `/actuator/health`'ini yoklar). CPU/RAM/pod/HPA/log **görselleri** Prometheus/metrics-server
> olmadığı için simülasyondur — panoya canlı his verir, rapora "tam metrik entegrasyonu gelecek
> çalışma" notu düşülebilir.

**Giriş**: Müşteri girişi **telefon numarası, ad veya kullanıcı adı** ile yapılabilir
(`0532 417 47 12`, `Elif`, `elif.aydin` — hepsi `elif.aydin` hesabına çözümlenir).

**Müşteri Portalı** (`elif.aydin` girişi) — sol menülü SPA:
- **Ana Sayfa**: gerçek tarife, güncel fatura, açık talep, kota çubukları (gerçek usage)
- **Kullanımım**: gerçek kota çubukları + **tarih aralığı sorgusu** (gerçek `GET /usage/.../history?from&to`)
- **Paket Değiştir**: gerçek tarife kataloğundan bir pakete geçince **abonelik + kota gerçekten güncellenir**
  (`PATCH /subscriptions/{id}/tariff` → `GET /tariffs/{code}` → `PUT /usage/subscriptions/{id}/quota`);
  Ana Sayfa'daki kota çubukları yeni pakete göre yenilenir. Altında esnek paket hesaplayıcısı (görsel).
- **Mağaza**: dijital servis + ev interneti (görsel demo)
- **Faturalarım**: gerçek faturalar; her satırda **Detay** (kalemler + Ara Toplam / Vergiler ÖİV+KDV / Genel Toplam)
  ve **PDF** (gerçek `GET /invoices/{id}/pdf`)
- **Taleplerim**: gerçek talep listesi + **Yeni Talep** (konu + ana/alt kategori + öncelik → gerçek `POST /tickets`)
- **Profilim**: gerçek müşteri/adres/iletişim verisi + düzenleme (`PUT /customers`, `POST /addresses`, `POST /contacts`)

> **Not:** `Paket Değiştir` ve kota güncelleme için usage-service (9006) ve subscription-service (9005)
> yeni uçları (`PUT .../quota`, `PATCH .../tariff`) içerir; bu iki servisi ve statik dosyalar değiştiği için
> gateway_server'ı (8080) yeniden başlatın.
