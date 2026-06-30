package com.turkcell.billing_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    UUID customerId,
    UUID subscriptionId,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal subTotal,
    BigDecimal tax,
    BigDecimal grandTotal,
    String status,
    LocalDate dueDate,
    LocalDateTime issuedAt
) {}