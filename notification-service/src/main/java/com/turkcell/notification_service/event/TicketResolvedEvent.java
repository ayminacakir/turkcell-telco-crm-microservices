package com.turkcell.notification_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketResolvedEvent(
        UUID eventId,
        String eventType,
        UUID ticketId,
        UUID customerId,
        LocalDateTime occurredAt
) {
}
