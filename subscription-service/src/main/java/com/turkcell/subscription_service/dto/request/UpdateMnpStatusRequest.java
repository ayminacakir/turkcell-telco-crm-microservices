package com.turkcell.subscription_service.dto.request;

import com.turkcell.subscription_service.enums.MnpStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateMnpStatusRequest(@NotNull MnpStatus mnpStatus) {
}
