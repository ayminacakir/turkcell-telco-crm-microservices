package com.turkcell.notification_service.messaging;

import com.turkcell.notification_service.domain.entity.ProcessedEvent;
import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.event.CustomerRegisteredEvent;
import com.turkcell.notification_service.repository.ProcessedEventRepository;
import com.turkcell.notification_service.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * customer.registered topic'ini dinler, yeni musteri icin welcome notification olusturur.
 * Aymina'nin gorev listesi madde 4.
 */
@Component
public class CustomerEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CustomerEventConsumer.class);

    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    public CustomerEventConsumer(NotificationService notificationService,
                                  ProcessedEventRepository processedEventRepository) {
        this.notificationService = notificationService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "customer.registered", groupId = "notification-service",
            containerFactory = "customerRegisteredContainerFactory")
    @Transactional
    public void onCustomerRegistered(CustomerRegisteredEvent event) {
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("customer.registered event zaten islenmis, atlaniyor: eventId={}", event.eventId());
            return;
        }

        log.info("customer.registered alindi: customerId={}", event.customerId());

        SendNotificationRequest request = new SendNotificationRequest(
                event.customerId(),
                "CUSTOMER_WELCOME",
                Map.of("fullName", event.fullName() == null ? "" : event.fullName())
        );
        notificationService.send(request);

        processedEventRepository.save(new ProcessedEvent(event.eventId(), "CustomerRegistered"));
    }
}
