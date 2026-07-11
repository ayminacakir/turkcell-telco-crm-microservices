package com.turkcell.billing_service.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceOverdueEvent(
        UUID eventId,
        String eventType,
        UUID invoiceId,
        UUID customerId,
        UUID subscriptionId,
        BigDecimal amountDue,
        LocalDate dueDate
) {}
