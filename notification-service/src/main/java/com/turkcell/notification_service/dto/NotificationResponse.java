package com.turkcell.notification_service.dto;

import com.turkcell.notification_service.domain.enums.NotificationChannel;
import com.turkcell.notification_service.domain.enums.NotificationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        String templateCode,
        NotificationChannel channel,
        NotificationStatus status,
        LocalDateTime sentAt
) {
}
