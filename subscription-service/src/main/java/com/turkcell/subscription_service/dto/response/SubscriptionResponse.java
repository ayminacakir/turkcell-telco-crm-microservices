package com.turkcell.subscription_service.dto.response;

import com.turkcell.subscription_service.enums.MnpStatus;
import com.turkcell.subscription_service.enums.SubscriptionStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID orderId,
        UUID customerId,
        String msisdn,
        String tariffCode,
        SubscriptionStatus status,
        MnpStatus mnpStatus,
        LocalDateTime activatedAt,
        LocalDateTime terminatedAt) {
}
