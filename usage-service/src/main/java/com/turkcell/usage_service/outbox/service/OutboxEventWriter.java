package com.turkcell.usage_service.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.usage_service.outbox.entity.OutboxEvent;
import com.turkcell.usage_service.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Domain islemleriyle ayni transaction icinde event'leri outbox_events tablosuna yazar.
 * Kafka'ya publish isini OutboxPublisherService scheduler'i yapar.
 */
@Service
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void write(UUID aggregateId, String aggregateType, String eventType, Object eventPayload) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setAggregateType(aggregateType);
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(serialize(eventPayload));
        outboxEventRepository.save(outboxEvent);
    }

    private String serialize(Object eventPayload) {
        try {
            return objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
