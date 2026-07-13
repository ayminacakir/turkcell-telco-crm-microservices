package com.turkcell.usage_service.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.turkcell.usage_service.domain.entity.Quota;
import com.turkcell.usage_service.event.SubscriptionActivatedEvent;
import com.turkcell.usage_service.kafka.entity.ProcessedEvent;
import com.turkcell.usage_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.usage_service.repository.QuotaRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaService {

    private final QuotaRepository quotaRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void createQuotaForSubscription(SubscriptionActivatedEvent event) {
        if (event.eventId() != null && processedEventRepository.existsById(event.eventId())) {
            log.warn("SubscriptionActivated event {} already processed, skipping", event.eventId());
            return;
        }

        if (quotaRepository.existsBySubscriptionId(event.subscriptionId())) {
            log.warn("Quota already exists for subscriptionId: {}, skipping", event.subscriptionId());
        } else {
            LocalDate periodStart = event.activatedAt().toLocalDate();
            LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);

            Quota quota = new Quota();
            quota.setSubscriptionId(event.subscriptionId());
            quota.setCustomerId(event.customerId());
            quota.setPeriodStart(periodStart);
            quota.setPeriodEnd(periodEnd);
            quota.setMinutesTotal(event.minutesIncluded() != null ? event.minutesIncluded() : 0);
            quota.setSmsTotal(event.smsIncluded() != null ? event.smsIncluded() : 0);
            quota.setMbTotal(event.dataMbIncluded() != null ? event.dataMbIncluded() : 0);
            quota.setMinutesRemaining(event.minutesIncluded());
            quota.setSmsRemaining(event.smsIncluded());
            quota.setMbRemaining(event.dataMbIncluded());
            quotaRepository.save(quota);

            log.info("Quota created for subscriptionId: {}, period: {} - {}",
                    event.subscriptionId(), periodStart, periodEnd);
        }

        if (event.eventId() != null) {
            processedEventRepository.save(new ProcessedEvent(event.eventId(), "SubscriptionActivated"));
        }
    }
}