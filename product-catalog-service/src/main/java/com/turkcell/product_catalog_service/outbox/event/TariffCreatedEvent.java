package com.turkcell.product_catalog_service.outbox.event;

import com.turkcell.product_catalog_service.domain.enums.TariffType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TariffCreatedEvent(
        UUID eventId,
        String eventType,
        UUID tariffId,
        String code,
        String name,
        TariffType type,
        BigDecimal monthlyFee,
        Integer minutesIncluded,
        Integer smsIncluded,
        Integer dataMbIncluded,
        LocalDateTime occurredAt
) {
}
