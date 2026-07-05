package com.turkcell.notification_service.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ⚠️ TASLAK — quota.threshold.reached / quota.exceeded payload formatı Mervenur ile
 * henüz netleşmedi (bkz. docs/event-contracts.md Açık Sorular #2 ve #5).
 * Alan isimleri buraya en makul tahminle yazıldı; gerçek format geldiğinde güncellenmeli.
 */
public record QuotaThresholdReachedEvent(
        UUID eventId,
        String eventType,
        UUID customerId,
        UUID subscriptionId,
        String msisdn,
        Integer thresholdPercentage,
        LocalDateTime occurredAt
) {
}
