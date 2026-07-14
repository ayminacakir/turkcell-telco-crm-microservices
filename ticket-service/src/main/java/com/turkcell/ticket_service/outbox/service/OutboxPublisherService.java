package com.turkcell.ticket_service.outbox.service;

import com.turkcell.ticket_service.outbox.entity.OutboxEvent;
import com.turkcell.ticket_service.outbox.enums.OutboxStatus;
import com.turkcell.ticket_service.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisherService(OutboxEventRepository outboxEventRepository,
                                   KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

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
                log.error("Failed to publish outbox event [{}]: {}", event.getId(), e.getMessage());
            }
        }
    }

    private String resolveTopicName(String eventType) {
        return switch (eventType) {
            case "TicketOpened" -> "ticket.opened";
            case "TicketResolved" -> "ticket.resolved";
            case "SlaBreached" -> "ticket.sla.breached";
            default -> null;
        };
    }
}
