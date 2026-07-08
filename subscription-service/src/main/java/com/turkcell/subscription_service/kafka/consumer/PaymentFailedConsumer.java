package com.turkcell.subscription_service.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.subscription_service.kafka.event.PaymentFailedEvent;
import com.turkcell.subscription_service.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentFailedConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentFailedConsumer.class);

    private final ObjectMapper objectMapper;
    private final SubscriptionService subscriptionService;

    public PaymentFailedConsumer(ObjectMapper objectMapper, SubscriptionService subscriptionService) {
        this.objectMapper = objectMapper;
        this.subscriptionService = subscriptionService;
    }

    @KafkaListener(topics = "payment.failed", groupId = "subscription-service-group")
    public void consumePaymentFailed(String payload) {
        LOGGER.info("Received payment.failed event payload={}", payload);
        try {
            PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
            subscriptionService.handlePaymentFailed(event);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to deserialize PaymentFailed event", e);
        }
    }
}
