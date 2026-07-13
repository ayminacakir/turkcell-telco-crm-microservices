package com.turkcell.notification_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * customer.registered topic'inden gelen event (Customer Service outbox ile publish ediyor).
 * TASLAK — Aymina ile alan isimleri teyit edilmeli.
 */
public record CustomerRegisteredEvent(
        UUID eventId,
        String eventType,
        UUID customerId,
        String fullName,
        String email,
        String msisdn,
        LocalDateTime registeredAt
) {
}
