package com.turkcell.notification_service.outbox.event;

import com.turkcell.notification_service.domain.enums.NotificationChannel;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationDispatchedEvent(
        UUID eventId,
        String eventType,
        UUID notificationId,
        UUID userId,
        String templateCode,
        NotificationChannel channel,
        LocalDateTime occurredAt
) {
}
