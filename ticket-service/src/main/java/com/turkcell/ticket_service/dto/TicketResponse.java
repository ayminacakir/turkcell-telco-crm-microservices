package com.turkcell.ticket_service.dto;

import com.turkcell.ticket_service.domain.enums.TicketPriority;
import com.turkcell.ticket_service.domain.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        UUID customerId,
        String category,
        TicketPriority priority,
        TicketStatus status,
        LocalDateTime slaDueAt,
        LocalDateTime createdAt,
        String assignedTeam
) {
}
