package com.turkcell.customer_service.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.turkcell.customer_service.enums.CustomerStatus;
import com.turkcell.customer_service.enums.CustomerType;

public record CustomerResponse(

                UUID id,
                CustomerType type,
                String firstName,
                String lastName,
                String companyName,
                String identityNumber,
                LocalDate dateOfBirth,
                CustomerStatus status,
                LocalDateTime createdAt) {

}
