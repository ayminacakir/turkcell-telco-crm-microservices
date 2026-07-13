package com.turkcell.billing_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.billing_service.event.UsageAggregatedEvent;
import com.turkcell.billing_service.service.UsageAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UsageAggregatedConsumer {

    private final ObjectMapper objectMapper;
    private final UsageAggregationService usageAggregationService;

    @KafkaListener(topics = "usage.aggregated", groupId = "billing-service-group")
    public void consumeUsageAggregated(String payload) {
        log.info("Received usage.aggregated event: {}", payload);
        try {
            UsageAggregatedEvent event = objectMapper.readValue(payload, UsageAggregatedEvent.class);
            usageAggregationService.handleUsageAggregated(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize UsageAggregatedEvent: {}", e.getMessage());
        }
    }
}
