-- Outbox publisher artik FAILED event'leri MAX_RETRIES'a kadar yeniden deniyor;
-- deneme sayisi bu kolonda tutulur.
ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
