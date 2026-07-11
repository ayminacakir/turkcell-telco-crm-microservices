package com.turkcell.usage_service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.usage_service.domain.entity.UsageRecord;
import com.turkcell.usage_service.dto.request.CreateUsageRecordRequest;
import com.turkcell.usage_service.event.CdrRecordedEvent;
import com.turkcell.usage_service.kafka.entity.ProcessedEvent;
import com.turkcell.usage_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.usage_service.service.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CdrRecordedConsumer {

    private final ObjectMapper objectMapper;
    private final UsageService usageService;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "cdr.recorded", groupId = "usage-service-group")
    public void consumeCdrRecorded(String payload) {
        log.info("Received cdr.recorded event: {}", payload);
        try {
            CdrRecordedEvent event = objectMapper.readValue(payload, CdrRecordedEvent.class);
            if (event.eventId() != null && processedEventRepository.existsById(event.eventId())) {
                log.warn("CdrRecorded event {} already processed, skipping", event.eventId());
                return;
            }

            UsageRecord.UsageType type = UsageRecord.UsageType.valueOf(event.type());
            usageService.recordUsage(new CreateUsageRecordRequest(
                    event.subscriptionId(), type, event.quantity(), event.cdrRef()));

            if (event.eventId() != null) {
                processedEventRepository.save(new ProcessedEvent(event.eventId(), "CdrRecorded"));
            }
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Failed to process CdrRecorded event: {}", e.getMessage());
        }
    }
}
