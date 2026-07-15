package com.turkcell.notification_service.dto;

import com.turkcell.notification_service.domain.enums.NotificationChannel;

import java.util.UUID;

public record NotificationPreferenceResponse(
        UUID userId,
        NotificationChannel channel,
        boolean optedOut
) {
}
