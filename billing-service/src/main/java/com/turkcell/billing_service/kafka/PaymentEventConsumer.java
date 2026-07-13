package com.turkcell.billing_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.billing_service.event.PaymentCompletedEvent;
import com.turkcell.billing_service.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;
    private final InvoiceService invoiceService;

    @KafkaListener(topics = "payment.completed", groupId = "billing-service-group")
    public void handlePaymentCompleted(String payload) {
        log.info("Received payment.completed event: {}", payload);
        try {
            PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
            invoiceService.handlePaymentCompleted(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize PaymentCompletedEvent: {}", e.getMessage());
        }
    }
}
