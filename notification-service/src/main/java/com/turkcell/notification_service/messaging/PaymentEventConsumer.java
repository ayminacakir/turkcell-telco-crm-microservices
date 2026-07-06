package com.turkcell.notification_service.messaging;

import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.event.PaymentCompletedEvent;
import com.turkcell.notification_service.event.PaymentFailedEvent;
import com.turkcell.notification_service.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * payment.completed / payment.failed topic'lerini dinler ve NotificationService.send()
 * üzerinden bildirim gönderir. templateCode eşlemesi docs/event-contracts.md #3'te tanımlı.
 *
 * NOT: Bu consumer'lar henüz production'da test edilmedi — Kafka broker ayağa kalkınca
 * (docker-compose ile) ve payment-service gerçekten publish etmeye başlayınca doğrulanmalı.
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final NotificationService notificationService;

    public PaymentEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "payment.completed", groupId = "notification-service",
            containerFactory = "paymentCompletedContainerFactory")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("payment.completed alındı: paymentId={}, orderId={}", event.paymentId(), event.orderId());

        SendNotificationRequest request = new SendNotificationRequest(
                event.customerId(),
                "PAYMENT_RECEIVED",
                Map.of(
                        "amount", event.amount().toString(),
                        "currency", event.currency(),
                        "orderId", event.orderId().toString()
                )
        );
        notificationService.send(request);
    }

    @KafkaListener(topics = "payment.failed", groupId = "notification-service",
            containerFactory = "paymentFailedContainerFactory")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("payment.failed alındı: paymentId={}, orderId={}, reason={}",
                event.paymentId(), event.orderId(), event.reason());

        SendNotificationRequest request = new SendNotificationRequest(
                event.customerId(),
                "PAYMENT_FAILED",
                Map.of(
                        "amount", event.amount().toString(),
                        "currency", event.currency(),
                        "reason", event.reason() == null ? "" : event.reason()
                )
        );
        notificationService.send(request);
    }
}
