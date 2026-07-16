-- QuotaEventConsumer ve PaymentEventConsumer'in referans verdigi sablonlar
-- simdiye kadar seed'lenmemisti; event geldiginde ResourceNotFoundException olusuyordu.
-- Kabul senaryosu 14.3 (Kota Asimi): %80'de uyari, %100'de ek paket onerisi.
INSERT INTO notification_service.notification_templates (id, code, channel, locale, subject, body_template)
VALUES
    (gen_random_uuid(), 'QUOTA_WARNING_80', 'SMS', 'tr',
        NULL,
        'Sayin musterimiz, paket kullaniminiz %{{thresholdPercentage}} seviyesine ulasmistir. Kalan kotanizi uygulamadan takip edebilirsiniz.'),
    (gen_random_uuid(), 'QUOTA_EXCEEDED', 'SMS', 'tr',
        NULL,
        'Sayin musterimiz, paket kotaniz dolmustur. Kesintisiz kullanim icin size ozel ek paketlere uygulamadan ulasabilirsiniz. Asim kullanimlari faturaniza yansitilacaktir.'),
    (gen_random_uuid(), 'PAYMENT_RECEIVED', 'SMS', 'tr',
        NULL,
        'Odemeniz alinmistir. Tutar: {{amount}} {{currency}}. Siparis no: {{orderId}}. Tesekkur ederiz.'),
    (gen_random_uuid(), 'PAYMENT_FAILED', 'SMS', 'tr',
        NULL,
        'Odemeniz alinamadi. Tutar: {{amount}} {{currency}}. Sebep: {{reason}}. Lutfen odeme yonteminizi kontrol edip tekrar deneyiniz.')
ON CONFLICT (code) DO NOTHING;

-- Kabul senaryosu 14.2 (Aylik Fatura): "InvoiceGenerated eventi ile notification
-- servisi e-posta atar" — fatura bildirimi SMS degil EMAIL kanalindan gitmeli.
UPDATE notification_service.notification_templates
SET channel = 'EMAIL',
    subject = 'Faturaniz olusturuldu'
WHERE code = 'INVOICE_GENERATED';
