package com.turkcell.ticket_service.dto;

import com.turkcell.ticket_service.domain.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TicketCreateRequest(
        @NotNull UUID customerId,
        @NotBlank String category,
        @NotNull TicketPriority priority,
        @NotBlank String description
) {
}
