package com.turkcell.ticket_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketCommentResponse(
        UUID id,
        UUID ticketId,
        UUID authorId,
        String body,
        LocalDateTime createdAt
) {
}
