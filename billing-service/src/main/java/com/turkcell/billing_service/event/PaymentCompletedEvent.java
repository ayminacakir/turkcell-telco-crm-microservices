package com.turkcell.billing_service.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * payment-service'in payment.completed topic'ine yayinladigi event kontrati.
 * invoiceId yalnizca fatura odemelerinde dolu gelir; siparis odemelerinde null'dir.
 */
public record PaymentCompletedEvent(
        UUID eventId,
        String eventType,
        UUID paymentId,
        UUID orderId,
        UUID invoiceId,
        UUID customerId,
        BigDecimal amount,
        String currency,
        String status,
        LocalDateTime paidAt,
        String tariffCode,
        Integer minutesIncluded,
        Integer smsIncluded,
        Integer dataMbIncluded
) {}
