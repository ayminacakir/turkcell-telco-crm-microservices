package com.turkcell.order_service.outbox.service;

import com.turkcell.order_service.outbox.entity.OutboxEvent;
import com.turkcell.order_service.outbox.enums.OutboxStatus;
import com.turkcell.order_service.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxStatus.PENDING);

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
                log.info("Published outbox event [{}] to topic [{}]", event.getId(), topic);
            } catch (Exception e) {
                event.setStatus(OutboxStatus.FAILED);
                outboxEventRepository.save(event);
                log.error("Failed to publish outbox event [{}] to topic [{}]: {}", event.getId(), topic, e.getMessage());
            }
        }
    }

    private String resolveTopicName(String eventType) {
        if ("OrderCreated".equals(eventType)) {
            return "order.created";
        }
        if ("OrderCancelled".equals(eventType)) {
            return "order.cancelled";
        }
        if ("OrderConfirmed".equals(eventType)) {
            return "order.confirmed";
        }
        return null;
    }
}
