package com.turkcell.notification_service.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * payment.failed topic'inden gelen event.
 * Format Aymina/Mervenur ile netleşti (bkz. docs/event-contracts.md #2.2).
 */
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
) {
}
