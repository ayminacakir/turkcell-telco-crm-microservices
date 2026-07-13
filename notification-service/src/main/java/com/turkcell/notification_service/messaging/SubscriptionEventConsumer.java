package com.turkcell.notification_service.messaging;

import com.turkcell.notification_service.domain.entity.ProcessedEvent;
import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.event.SubscriptionActivatedEvent;
import com.turkcell.notification_service.repository.ProcessedEventRepository;
import com.turkcell.notification_service.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * subscription.activated topic'ini dinler, welcome SMS gonderir.
 * Senaryo 1 (Yeni Abone Onboarding), Adim 6.
 */
@Component
public class SubscriptionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventConsumer.class);

    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    public SubscriptionEventConsumer(NotificationService notificationService,
                                      ProcessedEventRepository processedEventRepository) {
        this.notificationService = notificationService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "subscription.activated", groupId = "notification-service",
            containerFactory = "subscriptionActivatedContainerFactory")
    @Transactional
    public void onSubscriptionActivated(SubscriptionActivatedEvent event) {
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("subscription.activated event zaten islenmis, atlaniyor: eventId={}", event.eventId());
            return;
        }

        log.info("subscription.activated alindi: subscriptionId={}, customerId={}",
                event.subscriptionId(), event.customerId());

        SendNotificationRequest request = new SendNotificationRequest(
                event.customerId(),
                "WELCOME_SMS",
                Map.of(
                        "tariffCode", event.tariffCode() == null ? "" : event.tariffCode(),
                        "msisdn", event.msisdn() == null ? "" : event.msisdn()
                )
        );
        notificationService.send(request);

        processedEventRepository.save(new ProcessedEvent(event.eventId(), "SubscriptionActivated"));
    }
}
