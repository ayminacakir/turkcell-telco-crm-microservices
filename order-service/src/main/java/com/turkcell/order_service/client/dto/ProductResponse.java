package com.turkcell.order_service.client.dto;

import java.math.BigDecimal;

public record ProductResponse(
        String code,
        String name,
        BigDecimal monthlyFee,
        BigDecimal price,
        String status,
        Integer minutesIncluded,
        Integer smsIncluded,
        Integer dataMbIncluded
) {}
