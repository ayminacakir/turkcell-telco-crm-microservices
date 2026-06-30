package com.turkcell.payment_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID invoiceId,
    BigDecimal amount,
    String method,
    String status,
    String externalRef,
    LocalDateTime paidAt
) {}