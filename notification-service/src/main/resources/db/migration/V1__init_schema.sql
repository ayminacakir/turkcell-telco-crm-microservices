CREATE SCHEMA IF NOT EXISTS notification_service;

CREATE TABLE IF NOT EXISTS notification_service.notification_templates (
    id             UUID PRIMARY KEY,
    code           VARCHAR(100) NOT NULL UNIQUE,
    channel        VARCHAR(20) NOT NULL,
    locale         VARCHAR(10) NOT NULL,
    subject        VARCHAR(255),
    body_template  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS notification_service.notifications (
    id             UUID PRIMARY KEY,
    user_id        UUID NOT NULL,
    template_code  VARCHAR(100) NOT NULL,
    channel        VARCHAR(20) NOT NULL,
    payload_json   TEXT,
    status         VARCHAR(20) NOT NULL,
    sent_at        TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notification_service.notifications(user_id);

-- Idempotent Kafka consumer icin: ayni eventId ikinci kez islenmesin diye
CREATE TABLE IF NOT EXISTS notification_service.processed_events (
    event_id      UUID PRIMARY KEY,
    event_type    VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMP NOT NULL DEFAULT now()
);
