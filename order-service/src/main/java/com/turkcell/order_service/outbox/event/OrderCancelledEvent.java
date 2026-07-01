package com.turkcell.order_service.outbox.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID eventId,
        String eventType,
        UUID orderId,
        UUID customerId,
        String reason,
        LocalDateTime occurredAt
) {}
