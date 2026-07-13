package com.turkcell.billing_service.event;

import java.time.LocalDate;
import java.util.UUID;

public record UsageAggregatedEvent(
        UUID eventId,
        String eventType,
        UUID subscriptionId,
        UUID customerId,
        LocalDate periodStart,
        LocalDate periodEnd,
        int voiceUsed,
        int smsUsed,
        int dataMbUsed,
        int overageVoiceMinutes,
        int overageSms,
        int overageDataMb
) {}
