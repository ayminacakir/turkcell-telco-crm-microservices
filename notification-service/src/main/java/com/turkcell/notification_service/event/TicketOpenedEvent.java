package com.turkcell.notification_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketOpenedEvent(
        UUID eventId,
        String eventType,
        UUID ticketId,
        UUID customerId,
        String category,
        String priority,
        LocalDateTime slaDueAt,
        LocalDateTime occurredAt
) {
}
