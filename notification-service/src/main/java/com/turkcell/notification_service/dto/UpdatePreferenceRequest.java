package com.turkcell.notification_service.dto;

import com.turkcell.notification_service.domain.enums.NotificationChannel;
import jakarta.validation.constraints.NotNull;

public record UpdatePreferenceRequest(
        @NotNull NotificationChannel channel,
        @NotNull Boolean optedOut
) {
}
