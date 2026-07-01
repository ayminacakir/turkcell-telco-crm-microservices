package com.turkcell.order_service.kafka.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        String eventType,
        UUID paymentId,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        String currency,
        String status,
        String reason,
        LocalDateTime failedAt
) {}
