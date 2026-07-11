package com.turkcell.usage_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuotaThresholdEvent {
    private UUID eventId;
    private String eventType;    // QuotaThresholdReached, QuotaExceeded
    private UUID subscriptionId;
    private String type;         // VOICE, SMS, DATA
    private String threshold;    // PERCENT_80, PERCENT_100
    private int remaining;
}
