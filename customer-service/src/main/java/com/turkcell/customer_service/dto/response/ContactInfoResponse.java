package com.turkcell.customer_service.dto.response;

import java.util.UUID;

import com.turkcell.customer_service.enums.ContactType;

public record ContactInfoResponse(
        UUID id,
        UUID customerId,
        ContactType type,
        String value,
        boolean primaryContact) {

}
