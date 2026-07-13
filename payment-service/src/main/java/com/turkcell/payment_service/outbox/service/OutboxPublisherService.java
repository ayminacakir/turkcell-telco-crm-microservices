package com.turkcell.payment_service.outbox.service;

import com.turkcell.payment_service.outbox.entity.OutboxEvent;
import com.turkcell.payment_service.outbox.enums.OutboxStatus;
import com.turkcell.payment_service.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private static final Map<String, String> TOPICS_BY_EVENT_TYPE = Map.of(
            "PaymentCompleted", "payment.completed",
            "PaymentFailed", "payment.failed",
            "PaymentRefunded", "payment.refunded"
    );

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxStatus.PENDING);

        for (OutboxEvent event : pendingEvents) {
            String topic = TOPICS_BY_EVENT_TYPE.get(event.getEventType());
            if (topic == null) {
                log.warn("No topic mapping for outbox event type {}, skipping event {}",
                        event.getEventType(), event.getId());
                continue;
            }

            try {
                kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload()).get();
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
                log.info("Published outbox event {} to topic {}", event.getId(), topic);
            } catch (ExecutionException | InterruptedException exception) {
                event.setStatus(OutboxStatus.FAILED);
                outboxEventRepository.save(event);
                log.error("Failed to publish outbox event {} to topic {}", event.getId(), topic, exception);
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
