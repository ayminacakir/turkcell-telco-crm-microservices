package com.turkcell.subscription_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ActivateSubscriptionRequest(
        @NotNull UUID orderId,
        @NotNull UUID customerId,
        @NotBlank String tariffCode) {
}
