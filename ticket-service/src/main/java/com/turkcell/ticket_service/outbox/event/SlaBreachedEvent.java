package com.turkcell.ticket_service.outbox.event;

import com.turkcell.ticket_service.domain.enums.TicketPriority;

import java.time.LocalDateTime;
import java.util.UUID;

public record SlaBreachedEvent(
        UUID eventId,
        String eventType,
        UUID ticketId,
        UUID customerId,
        TicketPriority priority,
        LocalDateTime slaDueAt,
        LocalDateTime occurredAt
) {
}
