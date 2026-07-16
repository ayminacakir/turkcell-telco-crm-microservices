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
 * quota.threshold.reached / quota.exceeded topic'lerini dinler.
 * Payload formati usage-service'in gercek koduyla dogrulandi (docs/event-contracts.md #2.5).
 *
 * NOT: usage-service event'e henuz customerId koymuyor (Acik Soru #6). customerId
 * gelmeyen event loglanip ATLANIR — aksi halde bildirim kime gidecegi bilinemez ve
 * consumer hata dongusune girer. usage-service alani ekledigi anda akis kendiliginden
 * calisir hale gelir.
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

        log.info("quota.threshold.reached alındı: subscriptionId={}, type={}, threshold={}",
                event.subscriptionId(), event.type(), event.threshold());

        if (event.customerId() == null) {
            log.warn("quota event'inde customerId yok — bildirim kime gidecegi bilinemiyor, atlaniyor. " +
                    "usage-service event'e customerId eklemeli (event-contracts.md Acik Soru #6). eventId={}",
                    event.eventId());
            processedEventRepository.save(new ProcessedEvent(event.eventId(), "QuotaThresholdReached"));
            return;
        }

        SendNotificationRequest request = new SendNotificationRequest(
                event.customerId(),
                "QUOTA_WARNING_80",
                Map.of("thresholdPercentage", thresholdAsPercentage(event.threshold()))
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

        log.info("quota.exceeded alındı: subscriptionId={}, type={}", event.subscriptionId(), event.type());

        if (event.customerId() == null) {
            log.warn("quota event'inde customerId yok — bildirim kime gidecegi bilinemiyor, atlaniyor. " +
                    "usage-service event'e customerId eklemeli (event-contracts.md Acik Soru #6). eventId={}",
                    event.eventId());
            processedEventRepository.save(new ProcessedEvent(event.eventId(), "QuotaExceeded"));
            return;
        }

        SendNotificationRequest request = new SendNotificationRequest(
                event.customerId(),
                "QUOTA_EXCEEDED",
                Map.of()
        );
        notificationService.send(request);

        processedEventRepository.save(new ProcessedEvent(event.eventId(), "QuotaExceeded"));
    }

    /** "PERCENT_80" → "80" — sablon placeholder'i sayisal deger bekliyor. */
    private String thresholdAsPercentage(String threshold) {
        if (threshold == null) {
            return "";
        }
        return threshold.startsWith("PERCENT_") ? threshold.substring("PERCENT_".length()) : threshold;
    }
}
