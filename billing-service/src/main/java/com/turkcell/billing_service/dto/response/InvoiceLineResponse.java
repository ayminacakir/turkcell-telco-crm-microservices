package com.turkcell.billing_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineResponse(
    UUID id,
    UUID invoiceId,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {}