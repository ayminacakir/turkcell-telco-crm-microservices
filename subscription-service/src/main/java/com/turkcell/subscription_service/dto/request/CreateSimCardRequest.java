package com.turkcell.subscription_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateSimCardRequest(
        @NotBlank String iccid,
        @NotBlank String imsi,
        @NotBlank String msisdn) {
}
