package com.turkcell.notification_service.dto;

import com.turkcell.notification_service.domain.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationTemplateCreateRequest(
        @NotBlank @Size(max = 100) String code,
        @NotNull NotificationChannel channel,
        @NotBlank @Size(max = 10) String locale,
        String subject,
        @NotBlank String bodyTemplate
) {
}
