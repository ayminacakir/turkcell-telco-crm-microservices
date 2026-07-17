ALTER TABLE notification_service.notifications
    DROP CONSTRAINT IF EXISTS notifications_status_check;

ALTER TABLE notification_service.notifications
    ADD CONSTRAINT notifications_status_check
    CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED', 'SKIPPED'));
