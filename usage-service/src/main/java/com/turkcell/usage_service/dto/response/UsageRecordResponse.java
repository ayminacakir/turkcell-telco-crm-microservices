package com.turkcell.usage_service.dto.response;

import com.turkcell.usage_service.domain.entity.UsageRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record UsageRecordResponse(
    UUID id,
    UUID subscriptionId,
    UsageRecord.UsageType type,
    BigDecimal quantity,
    LocalDateTime recordedAt,
    String cdrRef
) {}