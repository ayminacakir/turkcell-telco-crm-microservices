package com.turkcell.subscription_service.dto.response;

import com.turkcell.subscription_service.enums.SimCardStatus;

public record SimCardResponse(
        String iccid,
        String imsi,
        String msisdn,
        SimCardStatus status) {
}
