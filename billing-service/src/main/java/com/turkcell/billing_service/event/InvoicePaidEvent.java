package com.turkcell.billing_service.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record InvoicePaidEvent(
        UUID eventId,
        String eventType,
        UUID invoiceId,
        UUID customerId,
        UUID subscriptionId,
        BigDecimal amountPaid,
        LocalDate paidAt
) {}
