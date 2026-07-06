package com.turkcell.subscription_service.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.subscription_service.kafka.event.PaymentCompletedEvent;
import com.turkcell.subscription_service.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentCompletedConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCompletedConsumer.class);

    private final ObjectMapper objectMapper;
    private final SubscriptionService subscriptionService;

    public PaymentCompletedConsumer(ObjectMapper objectMapper,
                                    SubscriptionService subscriptionService) {
        this.objectMapper = objectMapper;
        this.subscriptionService = subscriptionService;
    }

    @KafkaListener(topics = "payment.completed", groupId = "subscription-service-group")
    public void consumePaymentCompleted(String payload) {
        LOGGER.info("Received payment.completed event payload={}", payload);
        try {
            PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
            subscriptionService.handlePaymentCompleted(event);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to deserialize PaymentCompleted event", e);
        }
    }
}
