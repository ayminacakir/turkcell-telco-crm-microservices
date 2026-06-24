package com.turkcell.customer_service.dto.request;

import java.time.LocalDate;

public record UpdateCustomerRequest(
        String firstName,
        String lastName,
        String companyName,
        LocalDate dateOfBirth) {
}