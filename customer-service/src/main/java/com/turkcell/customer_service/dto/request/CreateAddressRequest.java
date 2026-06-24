package com.turkcell.customer_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateAddressRequest(
        @NotBlank String line1,

        @NotBlank String city,

        @NotBlank String district,

        String postalCode,

        boolean defaultAddress) {

}
