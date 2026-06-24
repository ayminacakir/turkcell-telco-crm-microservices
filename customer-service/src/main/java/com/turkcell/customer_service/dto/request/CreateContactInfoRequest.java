package com.turkcell.customer_service.dto.request;

import com.turkcell.customer_service.enums.ContactType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateContactInfoRequest(
        @NotBlank String value,
        @NotNull ContactType type,
        boolean primaryContact) {

}
