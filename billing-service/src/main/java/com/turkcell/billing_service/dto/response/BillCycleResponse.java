package com.turkcell.billing_service.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record BillCycleResponse(
    UUID id,
    UUID customerId,
    int dayOfMonth,
    LocalDate nextRunDate
) {}