package com.turkcell.billing_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateBillCycleRequest(
    @NotNull UUID customerId,
    @Min(1) @Max(28) int dayOfMonth
) {}