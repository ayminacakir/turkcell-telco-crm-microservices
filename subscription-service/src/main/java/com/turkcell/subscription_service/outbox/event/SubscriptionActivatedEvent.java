package com.turkcell.subscription_service.outbox.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record SubscriptionActivatedEvent(
        UUID eventId,
        String eventType,
        UUID orderId,
        UUID subscriptionId,
        UUID customerId,
        String tariffCode,
        String msisdn,
        Integer minutesIncluded,
        Integer smsIncluded,
        Integer dataMbIncluded,
        LocalDateTime activatedAt
) {
}
