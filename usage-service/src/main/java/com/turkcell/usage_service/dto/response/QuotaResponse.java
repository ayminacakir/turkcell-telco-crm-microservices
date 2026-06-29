package com.turkcell.usage_service.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record QuotaResponse(
    UUID id,
    UUID subscriptionId,
    LocalDate periodStart,
    LocalDate periodEnd,
    int minutesRemaining,
    int smsRemaining,
    int mbRemaining
) {}