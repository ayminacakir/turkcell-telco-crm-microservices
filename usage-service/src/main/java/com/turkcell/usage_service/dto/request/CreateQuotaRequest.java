package com.turkcell.usage_service.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateQuotaRequest(
    @NotNull UUID subscriptionId,
    @NotNull LocalDate periodStart,
    @NotNull LocalDate periodEnd,
    int minutesTotal,
    int smsTotal,
    int mbTotal
) {}