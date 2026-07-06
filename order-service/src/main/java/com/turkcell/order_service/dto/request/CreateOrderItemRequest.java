package com.turkcell.order_service.dto.request;

import com.turkcell.order_service.enums.OrderProductType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderItemRequest(

        // kept for backward compatibility, may be null in new flow
        UUID productId,

        // new contract fields
        @NotBlank String productCode,

        @NotNull OrderProductType productType,

        @NotBlank String productName,

        @NotNull @Min(1) Integer quantity,

        // unitPrice is optional in the new flow (may be null)
        BigDecimal unitPrice) {
}