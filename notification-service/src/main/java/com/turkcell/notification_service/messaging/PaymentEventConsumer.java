package com.turkcell.notification_service.messaging;

import com.turkcell.notification_service.domain.entity.ProcessedEvent;
import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.event.PaymentCompletedEvent;
import com.turkcell.notification_service.event.PaymentFailedEvent;
import com.turkcell.notification_service.repository.ProcessedEventRepository;
import com.turkcell.notification_service.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * payment.completed / payment.failed topic'lerini dinler ve NotificationService.send()
 * üzerinden bildirim gönderir. templateCode eşlemesi docs/event-contracts.md #3'te tanımlı.
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    public PaymentEventConsumer(NotificationService notificationService,
                                 ProcessedEventRepository processedEventRepository) {
        this.notificationService = notificationService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "payment.completed", groupId = "notification-service",
            containerFactory = "paymentCompletedContainerFactory")
    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("payment.completed event zaten islenmis, atlaniyor: eventId={}", event.eventId());
            return;
        }

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

        processedEventRepository.save(new ProcessedEvent(event.eventId(), "PaymentCompleted"));
    }

    @KafkaListener(topics = "payment.failed", groupId = "notification-service",
            containerFactory = "paymentFailedContainerFactory")
    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("payment.failed event zaten islenmis, atlaniyor: eventId={}", event.eventId());
            return;
        }

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

        processedEventRepository.save(new ProcessedEvent(event.eventId(), "PaymentFailed"));
    }
}
