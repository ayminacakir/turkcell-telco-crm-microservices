package com.turkcell.order_service.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.order_service.kafka.event.SubscriptionActivatedEvent;
import com.turkcell.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionEventConsumer {

    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    @KafkaListener(topics = "subscription.activated", groupId = "order-service-group")
    public void consumeSubscriptionActivated(String payload) {
        log.info("Received subscription.activated event: {}", payload);
        try {
            SubscriptionActivatedEvent event = objectMapper.readValue(payload, SubscriptionActivatedEvent.class);
            orderService.handleSubscriptionActivated(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize SubscriptionActivatedEvent: {}", e.getMessage());
        }
    }
}
