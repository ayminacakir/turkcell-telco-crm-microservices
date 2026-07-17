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
