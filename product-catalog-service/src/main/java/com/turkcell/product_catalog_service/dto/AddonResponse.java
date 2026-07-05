package com.turkcell.product_catalog_service.dto;

import com.turkcell.product_catalog_service.domain.enums.AddonType;

import java.math.BigDecimal;
import java.util.UUID;

public record AddonResponse(
        UUID id,
        String code,
        String name,
        BigDecimal price,
        String currency,
        AddonType type,
        Integer validityDays
) {
}
