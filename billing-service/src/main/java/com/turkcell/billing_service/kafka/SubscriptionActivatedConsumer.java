package com.turkcell.billing_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.billing_service.event.SubscriptionActivatedEvent;
import com.turkcell.billing_service.service.BillCycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionActivatedConsumer {

    private final ObjectMapper objectMapper;
    private final BillCycleService billCycleService;

    @KafkaListener(topics = "subscription.activated", groupId = "billing-service-group")
    public void consumeSubscriptionActivated(String payload) {
        log.info("Received subscription.activated event: {}", payload);
        try {
            SubscriptionActivatedEvent event = objectMapper.readValue(payload, SubscriptionActivatedEvent.class);
            billCycleService.createBillCycleForSubscription(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize SubscriptionActivatedEvent: {}", e.getMessage());
        }
    }
}