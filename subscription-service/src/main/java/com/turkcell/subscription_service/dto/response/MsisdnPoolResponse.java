package com.turkcell.subscription_service.dto.response;

import com.turkcell.subscription_service.enums.MsisdnStatus;
import java.time.LocalDateTime;

public record MsisdnPoolResponse(
        String msisdn,
        MsisdnStatus status,
        LocalDateTime reservedUntil) {
}
