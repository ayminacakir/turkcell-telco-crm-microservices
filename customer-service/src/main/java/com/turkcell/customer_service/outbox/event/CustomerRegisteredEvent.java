package com.turkcell.customer_service.outbox.event;

import com.turkcell.customer_service.enums.CustomerType;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerRegisteredEvent(
        UUID eventId,
        String eventType,
        UUID customerId,
        CustomerType customerType,
        String firstName,
        String lastName,
        String companyName,
        String identityNumber,
        LocalDateTime occurredAt
) {}
