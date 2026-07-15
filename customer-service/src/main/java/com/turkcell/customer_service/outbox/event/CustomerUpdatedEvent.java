package com.turkcell.customer_service.outbox.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerUpdatedEvent(
        UUID eventId,
        String eventType,
        UUID customerId,
        String firstName,
        String lastName,
        String companyName,
        LocalDateTime occurredAt
) {}
