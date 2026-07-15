package com.turkcell.notification_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * subscription.activated topic'inden gelen event.
 * TASLAK — Aymina/Mervenur ile alan isimleri teyit edilmeli (bkz. grup mesaji:
 * "customerId, subscriptionId, tariffCode, msisdn olmasi lazim").
 */
public record SubscriptionActivatedEvent(
        UUID eventId,
        String eventType,
        UUID subscriptionId,
        UUID orderId,
        UUID customerId,
        String tariffCode,
        String msisdn,
        LocalDateTime activatedAt
) {
}
