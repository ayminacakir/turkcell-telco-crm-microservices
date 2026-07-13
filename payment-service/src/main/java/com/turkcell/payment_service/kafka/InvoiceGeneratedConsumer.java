package com.turkcell.payment_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.payment_service.event.InvoiceGeneratedEvent;
import com.turkcell.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceGeneratedConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    @KafkaListener(topics = "invoice.generated", groupId = "payment-service-group")
    public void consumeInvoiceGenerated(String payload) {
        log.info("Received invoice.generated event: {}", payload);
        try {
            InvoiceGeneratedEvent event = objectMapper.readValue(payload, InvoiceGeneratedEvent.class);
            paymentService.handleInvoiceGenerated(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize InvoiceGeneratedEvent: {}", e.getMessage());
        }
    }
}
