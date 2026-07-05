package com.turkcell.notification_service.service;

import com.turkcell.notification_service.dto.NotificationResponse;
import com.turkcell.notification_service.dto.SendNotificationRequest;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    /**
     * Bildirim gönderir. Şu an bu metot REST üzerinden manuel tetikleniyor;
     * ileride Kafka consumer'lar (quota.threshold.reached, payment.completed vb.)
     * event'i bu DTO'ya map edip aynı metodu çağıracak.
     */
    NotificationResponse send(SendNotificationRequest request);

    NotificationResponse getById(UUID id);

    List<NotificationResponse> getByUserId(UUID userId);
}
