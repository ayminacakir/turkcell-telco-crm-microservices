package com.turkcell.payment_service.event;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderPaymentCompletedEvent(
        UUID paymentId,
        UUID orderId,
        UUID customerId,
        String tariffCode,
        Integer minutesIncluded,
        Integer smsIncluded,
        Integer dataMbIncluded,
        BigDecimal amount,
        String currency
) {}