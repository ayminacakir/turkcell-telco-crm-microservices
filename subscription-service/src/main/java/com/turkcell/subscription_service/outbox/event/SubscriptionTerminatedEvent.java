package com.turkcell.subscription_service.outbox.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record SubscriptionTerminatedEvent(
        UUID eventId,
        String eventType,
        UUID subscriptionId,
        UUID customerId,
        String msisdn,
        String tariffCode,
        LocalDateTime occurredAt
) {}
