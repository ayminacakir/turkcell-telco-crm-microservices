package com.turkcell.notification_service.dto;

import com.turkcell.notification_service.domain.enums.NotificationChannel;

import java.util.UUID;

public record NotificationTemplateResponse(
        UUID id,
        String code,
        NotificationChannel channel,
        String locale,
        String subject,
        String bodyTemplate
) {
}
