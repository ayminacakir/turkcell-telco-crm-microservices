package com.turkcell.ticket_service.outbox.event;

import com.turkcell.ticket_service.domain.enums.TicketPriority;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketOpenedEvent(
        UUID eventId,
        String eventType,
        UUID ticketId,
        UUID customerId,
        String category,
        TicketPriority priority,
        LocalDateTime slaDueAt,
        LocalDateTime occurredAt
) {
}
