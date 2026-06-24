package com.turkcell.customer_service.dto.request;

import com.turkcell.customer_service.enums.CustomerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateCustomerRequest(

                @NotNull CustomerType type,

                String firstName,

                String lastName,

                String companyName,

                @NotBlank String identityNumber,

                LocalDate dateOfBirth) {
}