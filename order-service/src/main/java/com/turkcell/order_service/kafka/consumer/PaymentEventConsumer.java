package com.turkcell.order_service.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.order_service.kafka.event.PaymentCompletedEvent;
import com.turkcell.order_service.kafka.event.PaymentFailedEvent;
import com.turkcell.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    @KafkaListener(topics = "payment.completed", groupId = "order-service-group")
    public void consumePaymentCompleted(String payload) {
        log.info("Received payment.completed event: {}", payload);
        try {
            PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
            orderService.handlePaymentCompleted(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize PaymentCompletedEvent: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.failed", groupId = "order-service-group")
    public void consumePaymentFailed(String payload) {
        log.info("Received payment.failed event: {}", payload);
        try {
            PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
            orderService.handlePaymentFailed(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize PaymentFailedEvent: {}", e.getMessage());
        }
    }
}
