package com.turkcell.product_catalog_service.outbox.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TariffPriceChangedEvent(
        UUID eventId,
        String eventType,
        UUID tariffId,
        String code,
        BigDecimal oldMonthlyFee,
        BigDecimal newMonthlyFee,
        LocalDateTime occurredAt
) {
}
