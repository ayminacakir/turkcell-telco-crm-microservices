package com.turkcell.order_service.outbox.event;

import com.turkcell.order_service.enums.OrderProductType;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreatedItemEvent(
        UUID productId,
        String productCode,
        OrderProductType productType,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        Integer minutesIncluded,
        Integer smsIncluded,
        Integer dataMbIncluded) {
}
