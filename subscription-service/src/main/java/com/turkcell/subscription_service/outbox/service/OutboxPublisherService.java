package com.turkcell.subscription_service.outbox.service;

import com.turkcell.subscription_service.outbox.entity.OutboxEvent;
import com.turkcell.subscription_service.outbox.enums.OutboxStatus;
import com.turkcell.subscription_service.outbox.repository.OutboxEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);
    /** FAILED bir event en fazla bu kadar kez yeniden denenir, sonra kalici FAILED kalir. */
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisherService(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusInAndRetryCountLessThan(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED), MAX_RETRIES);

        for (OutboxEvent event : pendingEvents) {
            String topic = resolveTopicName(event.getEventType());
            if (topic == null) {
                log.warn("No topic mapping for eventType: {}", event.getEventType());
                continue;
            }

            try {
                kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload()).get();
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
                log.info("Published outbox event {} to topic {}", event.getId(), topic);
            } catch (ExecutionException | InterruptedException exception) {
                event.setRetryCount(event.getRetryCount() + 1);
                event.setStatus(OutboxStatus.FAILED);
                outboxEventRepository.save(event);
                log.error("Failed to publish outbox event {} to topic {}", event.getId(), topic, exception);
                Thread.currentThread().interrupt();
            }
        }
    }

    private String resolveTopicName(String eventType) {
        return switch (eventType) {
            case "SubscriptionActivated"  -> "subscription.activated";
            case "SubscriptionSuspended"  -> "subscription.suspended";
            case "SubscriptionTerminated" -> "subscription.terminated";
            default -> null;
        };
    }
}
