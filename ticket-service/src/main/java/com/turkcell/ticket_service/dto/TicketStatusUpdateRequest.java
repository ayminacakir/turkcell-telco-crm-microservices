package com.turkcell.ticket_service.dto;

import com.turkcell.ticket_service.domain.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record TicketStatusUpdateRequest(
        @NotNull TicketStatus status
) {
}
