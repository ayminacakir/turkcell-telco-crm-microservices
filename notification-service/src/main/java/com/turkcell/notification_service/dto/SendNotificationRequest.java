package com.turkcell.notification_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Bildirim gönderme isteği. Şu an manual trigger (REST) ile kullanılıyor;
 * Kafka entegrasyonu geldiğinde (quota.threshold.reached, payment.completed vb.)
 * consumer'lar event payload'ını bu DTO'ya map edip aynı NotificationService.send()
 * metodunu çağıracak. Böylece iş mantığı tek yerde kalır.
 */
public record SendNotificationRequest(
        @NotNull UUID userId,
        @NotBlank String templateCode,
        Map<String, String> placeholders
) {
}
