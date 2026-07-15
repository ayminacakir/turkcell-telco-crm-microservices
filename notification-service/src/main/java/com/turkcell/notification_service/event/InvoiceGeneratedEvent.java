package com.turkcell.notification_service.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * invoice.generated topic'inden gelen event (Billing Service, bill-run sonrasi publish ediyor).
 * TASLAK — Mervenur ile alan isimleri teyit edilmeli.
 */
public record InvoiceGeneratedEvent(
        UUID eventId,
        String eventType,
        UUID invoiceId,
        UUID customerId,
        UUID subscriptionId,
        BigDecimal amount,
        String currency,
        LocalDate dueDate,
        LocalDateTime generatedAt
) {
}
