package com.turkcell.subscription_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateMsisdnPoolRequest(@NotBlank String msisdn) {
}
