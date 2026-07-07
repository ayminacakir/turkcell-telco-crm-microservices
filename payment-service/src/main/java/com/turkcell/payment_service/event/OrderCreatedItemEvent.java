package com.turkcell.payment_service.event;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreatedItemEvent(
        UUID productId,
        String productCode,
        String productType,   // Order Service'te enum (OrderProductType), burada String yeterli - Jackson otomatik map eder
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        Integer minutesIncluded,
        Integer smsIncluded,
        Integer dataMbIncluded
) {}