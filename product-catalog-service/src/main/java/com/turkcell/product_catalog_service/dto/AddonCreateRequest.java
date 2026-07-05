package com.turkcell.product_catalog_service.dto;

import com.turkcell.product_catalog_service.domain.enums.AddonType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record AddonCreateRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank String name,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal price,
        @NotNull AddonType type,
        @PositiveOrZero Integer validityDays
) {
}
