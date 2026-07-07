package com.turkcell.payment_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.payment_service.event.OrderCreatedEvent;
import com.turkcell.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderCreatedConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    @KafkaListener(topics = "order.created", groupId = "payment-service-group")
    public void consumeOrderCreated(String payload) {
        log.info("Received order.created event: {}", payload);
        try {
            OrderCreatedEvent event = objectMapper.readValue(payload, OrderCreatedEvent.class);
            paymentService.handleOrderCreated(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize OrderCreatedEvent: {}", e.getMessage());
        }
    }
}