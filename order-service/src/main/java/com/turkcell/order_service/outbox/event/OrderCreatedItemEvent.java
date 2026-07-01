package com.turkcell.order_service.outbox.event;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreatedItemEvent(
        UUID productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {
}
