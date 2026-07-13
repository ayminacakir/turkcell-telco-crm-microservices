package com.turkcell.billing_service.service;

import com.turkcell.billing_service.domain.entity.UsageAggregation;
import com.turkcell.billing_service.event.UsageAggregatedEvent;
import com.turkcell.billing_service.kafka.entity.ProcessedEvent;
import com.turkcell.billing_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.billing_service.repository.UsageAggregationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageAggregationService {

    private final UsageAggregationRepository usageAggregationRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void handleUsageAggregated(UsageAggregatedEvent event) {
        if (event.eventId() != null && processedEventRepository.existsById(event.eventId())) {
            log.warn("UsageAggregated event {} already processed, skipping", event.eventId());
            return;
        }

        UsageAggregation aggregation = usageAggregationRepository
                .findBySubscriptionIdAndPeriodStartAndPeriodEnd(
                        event.subscriptionId(), event.periodStart(), event.periodEnd())
                .orElseGet(UsageAggregation::new);

        aggregation.setSubscriptionId(event.subscriptionId());
        aggregation.setCustomerId(event.customerId());
        aggregation.setPeriodStart(event.periodStart());
        aggregation.setPeriodEnd(event.periodEnd());
        aggregation.setVoiceUsed(event.voiceUsed());
        aggregation.setSmsUsed(event.smsUsed());
        aggregation.setDataMbUsed(event.dataMbUsed());
        aggregation.setOverageVoiceMinutes(event.overageVoiceMinutes());
        aggregation.setOverageSms(event.overageSms());
        aggregation.setOverageDataMb(event.overageDataMb());
        usageAggregationRepository.save(aggregation);

        if (event.eventId() != null) {
            processedEventRepository.save(new ProcessedEvent(event.eventId(), "UsageAggregated"));
        }
        log.info("Usage aggregation stored for subscriptionId={}", event.subscriptionId());
    }

    public UsageAggregation findForPeriod(java.util.UUID subscriptionId,
                                          java.time.LocalDate periodStart,
                                          java.time.LocalDate periodEnd) {
        return usageAggregationRepository
                .findBySubscriptionIdAndPeriodStartAndPeriodEnd(subscriptionId, periodStart, periodEnd)
                .orElse(null);
    }
}
