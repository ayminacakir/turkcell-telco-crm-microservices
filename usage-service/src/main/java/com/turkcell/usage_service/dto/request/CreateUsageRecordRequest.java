package com.turkcell.usage_service.dto.request;

import com.turkcell.usage_service.domain.entity.UsageRecord;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateUsageRecordRequest(
    @NotNull UUID subscriptionId,
    @NotNull UsageRecord.UsageType type,
    @NotNull BigDecimal quantity,
    String cdrRef
) {}