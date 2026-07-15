package com.turkcell.customer_service.outbox.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerKYCRejectedEvent(
        UUID eventId,
        String eventType,
        UUID customerId,
        LocalDateTime occurredAt
) {}
