CREATE TABLE IF NOT EXISTS notification_service.notification_preferences (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL,
    channel    VARCHAR(20) NOT NULL,
    opted_out  BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_notification_preferences_user_channel UNIQUE (user_id, channel)
);

CREATE INDEX IF NOT EXISTS idx_notification_preferences_user_id ON notification_service.notification_preferences(user_id);
