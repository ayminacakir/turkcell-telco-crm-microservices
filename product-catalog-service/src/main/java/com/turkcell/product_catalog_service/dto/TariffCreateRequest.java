package com.turkcell.product_catalog_service.dto;

import com.turkcell.product_catalog_service.domain.enums.TariffType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TariffCreateRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank String name,
        @NotNull TariffType type,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal monthlyFee,
        @PositiveOrZero Integer minutesIncluded,
        @PositiveOrZero Integer smsIncluded,
        @PositiveOrZero Integer dataMbIncluded,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
