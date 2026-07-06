# CLAUDE.MD

> Bu governance dosyası proje genelinde çalışan insan veya
> yapay zekaların tamamının uyması **ZORUNLU** olan kurallar
> bütününü içeren dosyadır.

---

## 1) Genel Çalışma Prensipleri

### 1.1) DOSYA LİMİTİ

Hangi işlem olursa olsun tek seferde maksimum 10 dosya düzenleme hakkına sahipsin. 10 dosya ve üzeri implementasyonlarda planı 10'ar dosyalık batchlere bölmek zorundasın.

### 1.2) UYDURMAK YASAK (NO INVENTING)

Eğer herhangi bir operasyonda bilgi ya da referans eksikliği varsa uydurma yapmak yasaktır. Operasyonu durdurup kullanıcıya sormak zorundasın.

### 1.3)  ÖNCE PLANLA, SONRA KODLA

Kod üretmeden önce şunları yapmak zorundasın;

- Bir dosya dökümü hazırla (hangi dosyalar değişecek/eklenecek/silinecek + neden)

---

## Order-service: OrderItem -> productCode/productType migration

Değiştirilen/eklenen dosyalar (order-service scope içinde):

- `src/main/java/com/turkcell/order_service/enums/OrderProductType.java` (yeni enum: TARIFF, ADDON)
- `src/main/java/com/turkcell/order_service/dto/request/CreateOrderItemRequest.java` (güncellendi: `productCode`, `productType` zorunlu; `productId` korunuyor; `unitPrice` opsiyonel)
- `src/main/java/com/turkcell/order_service/entity/OrderItem.java` (güncellendi: `productCode`, `productType`, `minutesIncluded`, `smsIncluded`, `dataMbIncluded` eklendi)
- `src/main/java/com/turkcell/order_service/dto/response/OrderItemResponse.java` (güncellendi: `productCode`, `productType` eklendi)
- `src/main/java/com/turkcell/order_service/outbox/event/OrderCreatedItemEvent.java` (güncellendi: yeni alanlar eklendi)
- `src/main/java/com/turkcell/order_service/client/ProductClient.java` (yeni Feign client, product lookup için)
- `src/main/java/com/turkcell/order_service/client/dto/ProductResponse.java` (yeni DTO)
- `src/main/java/com/turkcell/order_service/service/OrderService.java` (güncellendi: validation, mapping, outbox payload kullanımı)

Notlar:
- Değişiklikler yalnızca `order-service` içinde yapıldı. Diğer servisler, root `pom.xml`, ve konfigürasyon dosyaları değiştirilmedi.
- Mevcut `payment.completed`, `payment.failed`, `subscription.activated` akışları ve Kafka outbox mantığı korunacak şekilde event payload üretimi güncellendi (yeni item alanları eklendi).
- `productId` alanı geriye uyumluluk için korunuyor; yeni create mantığı `productCode`/`productType` üzerinden çalışıyor.


- Eğer varsa yeni bağımlılıklar matrisi (Hangi kütüphane, versiyon + neden)

- Planı sun, onay almadan asla implementasyona başlama.