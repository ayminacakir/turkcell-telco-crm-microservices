package com.turkcell.order_service.dto.response;

import com.turkcell.order_service.enums.OrderProductType;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        String productCode,
        OrderProductType productType,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {
}