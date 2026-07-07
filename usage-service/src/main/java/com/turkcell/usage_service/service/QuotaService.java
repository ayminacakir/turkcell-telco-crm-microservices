package com.turkcell.usage_service.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.turkcell.usage_service.domain.entity.Quota;
import com.turkcell.usage_service.event.SubscriptionActivatedEvent;
import com.turkcell.usage_service.repository.QuotaRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaService {

    private final QuotaRepository quotaRepository;

    @Transactional
    public void createQuotaForSubscription(SubscriptionActivatedEvent event) {
        if (quotaRepository.existsBySubscriptionId(event.subscriptionId())) {
            log.warn("Quota already exists for subscriptionId: {}, skipping", event.subscriptionId());
            return;
        }

        LocalDate periodStart = event.activatedAt().toLocalDate();
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);

        Quota quota = new Quota();
        quota.setSubscriptionId(event.subscriptionId());
        quota.setPeriodStart(periodStart);
        quota.setPeriodEnd(periodEnd);
        quota.setMinutesRemaining(event.minutesIncluded());
        quota.setSmsRemaining(event.smsIncluded());
        quota.setMbRemaining(event.dataMbIncluded());
        quotaRepository.save(quota);

        log.info("Quota created for subscriptionId: {}, period: {} - {}", event.subscriptionId(), periodStart, periodEnd);
    }
}