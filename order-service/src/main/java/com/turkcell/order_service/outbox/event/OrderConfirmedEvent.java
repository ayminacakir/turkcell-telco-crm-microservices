package com.turkcell.order_service.outbox.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderConfirmedEvent(
        UUID eventId,
        String eventType,
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount,
        String currency,
        LocalDateTime occurredAt
) {}
