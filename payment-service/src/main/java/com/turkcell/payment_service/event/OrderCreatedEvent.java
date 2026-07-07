package com.turkcell.payment_service.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        String eventType,
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount,
        String currency,
        List<OrderCreatedItemEvent> items,
        LocalDateTime occurredAt
) {}