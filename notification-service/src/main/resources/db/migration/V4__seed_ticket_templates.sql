INSERT INTO notification_service.notification_templates (id, code, channel, locale, subject, body_template)
VALUES
    (gen_random_uuid(), 'TICKET_OPENED', 'SMS', 'tr',
        NULL,
        'Talebiniz alinmistir. Kategori: {{category}}, Oncelik: {{priority}}. En kisa surede donus yapilacaktir.'),
    (gen_random_uuid(), 'TICKET_RESOLVED', 'SMS', 'tr',
        NULL,
        'Talebiniz cozume kavusturulmustur. Ilginiz icin tesekkur ederiz.')
ON CONFLICT (code) DO NOTHING;
