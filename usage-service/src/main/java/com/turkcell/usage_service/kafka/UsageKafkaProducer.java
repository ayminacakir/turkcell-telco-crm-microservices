package com.turkcell.usage_service.kafka;

import com.turkcell.usage_service.event.QuotaThresholdEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageKafkaProducer {

    private final KafkaTemplate<String, QuotaThresholdEvent> kafkaTemplate;

    public static final String QUOTA_THRESHOLD_TOPIC = "quota.threshold.reached";
    public static final String QUOTA_EXCEEDED_TOPIC = "quota.exceeded";

    public void publishThresholdReached(QuotaThresholdEvent event) {
        kafkaTemplate.send(QUOTA_THRESHOLD_TOPIC, event.getSubscriptionId().toString(), event);
        log.info("QuotaThreshold event published: {} - {}", event.getType(), event.getThreshold());
    }

    public void publishQuotaExceeded(QuotaThresholdEvent event) {
        kafkaTemplate.send(QUOTA_EXCEEDED_TOPIC, event.getSubscriptionId().toString(), event);
        log.info("QuotaExceeded event published: {}", event.getSubscriptionId());
    }
}