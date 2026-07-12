INSERT INTO notification_service.notification_templates (id, code, channel, locale, subject, body_template)
VALUES
    (gen_random_uuid(), 'WELCOME_SMS', 'SMS', 'tr',
        NULL,
        'Sayin musterimiz, {{tariffCode}} tarifeniz {{msisdn}} numarasi ile aktif edilmistir. Hosgeldiniz!'),
    (gen_random_uuid(), 'INVOICE_GENERATED', 'SMS', 'tr',
        NULL,
        'Faturaniz olusturulmustur. Tutar: {{amount}} {{currency}}. Son odeme tarihi: {{dueDate}}.'),
    (gen_random_uuid(), 'CUSTOMER_WELCOME', 'SMS', 'tr',
        NULL,
        'Sayin {{fullName}}, Turkcell ailesine hosgeldiniz!')
ON CONFLICT (code) DO NOTHING;
