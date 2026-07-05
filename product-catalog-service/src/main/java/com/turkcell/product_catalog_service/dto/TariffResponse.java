package com.turkcell.product_catalog_service.dto;

import com.turkcell.product_catalog_service.domain.enums.TariffStatus;
import com.turkcell.product_catalog_service.domain.enums.TariffType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Order Service ve diğer tüketiciler için sözleşme (contract) DTO'su.
 * Dışa "code" ile referans verilir; internal UUID id sadece bilgi amaçlı taşınır.
 */
public record TariffResponse(
        UUID id,
        String code,
        String name,
        TariffType type,
        BigDecimal monthlyFee,
        String currency,
        Integer minutesIncluded,
        Integer smsIncluded,
        Integer dataMbIncluded,
        TariffStatus status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
