package com.turkcell.usage_service.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CdrRecordedEvent(
        UUID eventId,
        String eventType,
        UUID subscriptionId,
        String type,
        BigDecimal quantity,
        String cdrRef,
        LocalDateTime recordedAt
) {}
