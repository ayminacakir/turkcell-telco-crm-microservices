package com.turkcell.usage_service.simulator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.usage_service.domain.entity.Quota;
import com.turkcell.usage_service.domain.entity.UsageRecord;
import com.turkcell.usage_service.event.CdrRecordedEvent;
import com.turkcell.usage_service.repository.QuotaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * CDR simulatoru: cdr.recorded topic'ine event yazar (FR-17).
 * Usage Service bu event'i consume ederek kullanimi isler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "usage.cdr-simulator", name = "enabled", havingValue = "true")
public class CdrSimulator {

    private static final String CDR_RECORDED_TOPIC = "cdr.recorded";
    private static final int MAX_VOICE_MINUTES = 60;
    private static final int MAX_SMS_COUNT = 20;
    private static final int MAX_DATA_MB = 500;

    private final QuotaRepository quotaRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @Scheduled(fixedDelayString = "${usage.cdr-simulator.fixed-delay-ms}")
    public void generateCdr() {
        LocalDate today = LocalDate.now();
        List<Quota> activeQuotas = quotaRepository.findAll().stream()
                .filter(q -> !q.getPeriodStart().isAfter(today) && !q.getPeriodEnd().isBefore(today))
                .toList();

        if (activeQuotas.isEmpty()) {
            log.debug("CDR simulator: no active quotas found, skipping");
            return;
        }

        Quota quota = activeQuotas.get(random.nextInt(activeQuotas.size()));
        UsageRecord.UsageType type = UsageRecord.UsageType.values()[random.nextInt(UsageRecord.UsageType.values().length)];
        BigDecimal quantity = BigDecimal.valueOf(randomQuantity(type));
        String cdrRef = "CDR-SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        CdrRecordedEvent event = new CdrRecordedEvent(
                UUID.randomUUID(),
                "CdrRecorded",
                quota.getSubscriptionId(),
                type.name(),
                quantity,
                cdrRef,
                LocalDateTime.now()
        );

        try {
            kafkaTemplate.send(CDR_RECORDED_TOPIC, quota.getSubscriptionId().toString(),
                    objectMapper.writeValueAsString(event));
            log.info("CDR simulator published cdr.recorded: subscriptionId={}, type={}, quantity={}, cdrRef={}",
                    quota.getSubscriptionId(), type, quantity, cdrRef);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize CdrRecordedEvent", e);
        }
    }

    private int randomQuantity(UsageRecord.UsageType type) {
        return switch (type) {
            case VOICE -> random.nextInt(MAX_VOICE_MINUTES) + 1;
            case SMS -> random.nextInt(MAX_SMS_COUNT) + 1;
            case DATA -> random.nextInt(MAX_DATA_MB) + 1;
        };
    }
}
