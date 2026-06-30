package com.turkcell.payment_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequest(
    @NotNull UUID invoiceId,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank String method   // CREDIT_CARD, BANK_TRANSFER, WALLET
) {}