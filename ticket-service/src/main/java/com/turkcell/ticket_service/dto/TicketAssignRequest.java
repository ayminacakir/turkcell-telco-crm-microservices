package com.turkcell.ticket_service.dto;

import jakarta.validation.constraints.NotBlank;

public record TicketAssignRequest(
        @NotBlank String team
) {
}
