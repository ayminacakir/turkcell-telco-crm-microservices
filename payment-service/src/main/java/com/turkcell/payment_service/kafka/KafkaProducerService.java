package com.turkcell.payment_service.kafka;

import com.turkcell.payment_service.event.PaymentCompletedEvent;
import com.turkcell.payment_service.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    public static final String PAYMENT_FAILED_TOPIC = "payment.failed";

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, event.getOrderId().toString(), event);
        log.info("PaymentCompleted event published for paymentId: {}", event.getPaymentId());
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        kafkaTemplate.send(PAYMENT_FAILED_TOPIC, event.getOrderId().toString(), event);
        log.info("PaymentFailed event published for paymentId: {}", event.getPaymentId());
    }
}