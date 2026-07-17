package com.turkcell.notification_service.event;

import java.util.UUID;

/**
 * quota.threshold.reached / quota.exceeded topic'lerinden gelen event.
 * Format usage-service'in GERCEK payload'i ile dogrulandi (origin/merve 054c48f,
 * usage_service.event.QuotaThresholdEvent) — bkz. docs/event-contracts.md #2.5.
 *
 * customerId usage-service tarafinda HENUZ publish edilmiyor; Quota entity'sinde
 * alan mevcut, event'e eklenmesi bekleniyor (event-contracts.md Acik Soru #6).
 * Eklenene kadar null gelir — consumer bu durumu loglayip bildirimi atlar.
 */
public record QuotaThresholdReachedEvent(
        UUID eventId,
        String eventType,      // QuotaThresholdReached | QuotaExceeded
        UUID customerId,       // simdilik null — usage-service eklemeli (Acik Soru #6)
        UUID subscriptionId,
        String type,           // VOICE | SMS | DATA
        String threshold,      // PERCENT_80 | PERCENT_100
        Integer remaining
) {
}
