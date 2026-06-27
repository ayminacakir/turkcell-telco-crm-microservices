package com.turkcell.billing_service.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateInvoiceRequest(
    @NotNull UUID customerId,
    @NotNull UUID subscriptionId,
    @NotNull LocalDate periodStart,
    @NotNull LocalDate periodEnd
) {}