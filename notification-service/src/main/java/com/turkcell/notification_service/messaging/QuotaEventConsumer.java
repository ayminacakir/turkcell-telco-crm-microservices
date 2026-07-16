package com.turkcell.notification_service.messaging;

import com.turkcell.notification_service.domain.entity.ProcessedEvent;
import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.event.QuotaThresholdReachedEvent;
import com.turkcell.notification_service.repository.ProcessedEventRepository;
import com.turkcell.notification_service.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * ⚠️ TASLAK — quota.threshold.reached / quota.exceeded payload formatı Mervenur ile
 * netleşmeden bu consumer'lar production'da güvenilir çalışmaz. QuotaThresholdReachedEvent
 * DTO'sundaki alan isimleri kesinleşince burası da güncellenmeli.
 */
@Component
public class QuotaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(QuotaEventConsumer.class);

    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    public QuotaEventConsumer(NotificationService notificationService,
                               ProcessedEventRepository processedEventRepository) {
        this.notificationService = notificationService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "quota.threshold.reached", groupId = "notification-service",
            containerFactory = "quotaEventContainerFactory")
    @Transactional
    public void onQuotaThresholdReached(QuotaThresholdReachedEvent event) {
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("quota.threshold.reached event zaten islenmis, atlaniyor: eventId={}", event.eventId());
            return;
        }

        log.info("quota.threshold.reached alındı: customerId={}, threshold={}%",
                event.customerId(), event.thresholdPercentage());

        SendNotificationRequest request = new SendNotificationRequest(
                event.customerId(),
                "QUOTA_WARNING_80",
                Map.of("thresholdPercentage", String.valueOf(event.thresholdPercentage()))
        );
        notificationService.send(request);

        processedEventRepository.save(new ProcessedEvent(event.eventId(), "QuotaThresholdReached"));
    }

    @KafkaListener(topics = "quota.exceeded", groupId = "notification-service",
            containerFactory = "quotaEventContainerFactory")
    @Transactional
    public void onQuotaExceeded(QuotaThresholdReachedEvent event) {
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("quota.exceeded event zaten islenmis, atlaniyor: eventId={}", event.eventId());
            return;
        }

        log.info("quota.exceeded alındı: customerId={}", event.customerId());

        SendNotificationRequest request = new SendNotificationRequest(
                event.customerId(),
                "QUOTA_EXCEEDED",
                Map.of()
        );
        notificationService.send(request);

        processedEventRepository.save(new ProcessedEvent(event.eventId(), "QuotaExceeded"));
    }
}
