package com.turkcell.usage_service.service.impl;

import com.turkcell.usage_service.domain.entity.Quota;
import com.turkcell.usage_service.domain.entity.UsageRecord;
import com.turkcell.usage_service.dto.request.CreateQuotaRequest;
import com.turkcell.usage_service.dto.request.CreateUsageRecordRequest;
import com.turkcell.usage_service.dto.response.QuotaResponse;
import com.turkcell.usage_service.dto.response.UsageRecordResponse;
import com.turkcell.usage_service.event.QuotaThresholdEvent;
import com.turkcell.usage_service.event.UsageAggregatedEvent;
import com.turkcell.usage_service.outbox.service.OutboxEventWriter;
import com.turkcell.usage_service.repository.QuotaRepository;
import com.turkcell.usage_service.repository.UsageRecordRepository;
import com.turkcell.usage_service.service.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageServiceImpl implements UsageService {

    private static final String QUOTA_AGGREGATE_TYPE = "Quota";
    private static final String QUOTA_THRESHOLD_REACHED_EVENT = "QuotaThresholdReached";
    private static final String QUOTA_EXCEEDED_EVENT = "QuotaExceeded";
    private static final String USAGE_AGGREGATED_EVENT = "UsageAggregated";

    private final UsageRecordRepository usageRecordRepository;
    private final QuotaRepository quotaRepository;
    private final OutboxEventWriter outboxEventWriter;

    @Override
    @Transactional
    public UsageRecordResponse recordUsage(CreateUsageRecordRequest request) {
        // Kullanım kaydı oluştur
        UsageRecord record = new UsageRecord();
        record.setSubscriptionId(request.subscriptionId());
        record.setType(request.type());
        record.setQuantity(request.quantity());
        record.setRecordedAt(LocalDateTime.now());
        record.setCdrRef(request.cdrRef());
        usageRecordRepository.save(record);

        // Aktif quota'yı güncelle
        LocalDate today = LocalDate.now();
        quotaRepository.findBySubscriptionIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
            request.subscriptionId(), today, today
        ).ifPresent(quota -> {
            int used = request.quantity().intValue();
            updateQuotaAndCheckThreshold(quota, request.type(), used);
        });

        return toResponse(record);
    }

    @Override
    public List<UsageRecordResponse> getHistory(UUID subscriptionId,
                                                  LocalDateTime from, LocalDateTime to) {
        return usageRecordRepository
            .findBySubscriptionIdAndRecordedAtBetween(subscriptionId, from, to)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public QuotaResponse getActiveQuota(UUID subscriptionId) {
        LocalDate today = LocalDate.now();
        Quota quota = quotaRepository
            .findBySubscriptionIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                subscriptionId, today, today)
            .orElseThrow(() -> new RuntimeException(
                "Active quota not found for subscription: " + subscriptionId));
        return toQuotaResponse(quota);
    }

    @Override
    public QuotaResponse createQuota(CreateQuotaRequest request) {
        Quota quota = new Quota();
        quota.setSubscriptionId(request.subscriptionId());
        quota.setPeriodStart(request.periodStart());
        quota.setPeriodEnd(request.periodEnd());
        quota.setMinutesRemaining(request.minutesTotal());
        quota.setSmsRemaining(request.smsTotal());
        quota.setMbRemaining(request.mbTotal());
        return toQuotaResponse(quotaRepository.save(quota));
    }

    // FR-19: %80 ve %100 eşik kontrolü + FR-20: aşım billing'e UsageAggregated ile gider
    private void updateQuotaAndCheckThreshold(Quota quota,
                                               UsageRecord.UsageType type, int used) {
        int beforeRemaining;
        int totalIncluded;
        int beforeOverage;

        switch (type) {
            case VOICE -> {
                beforeRemaining = quota.getMinutesRemaining();
                totalIncluded = quota.getMinutesTotal() != null ? quota.getMinutesTotal() : beforeRemaining + used;
                beforeOverage = quota.getOverageVoiceMinutes();
                quota.setVoiceUsed(quota.getVoiceUsed() + used);
                if (used <= beforeRemaining) {
                    quota.setMinutesRemaining(beforeRemaining - used);
                } else {
                    quota.setMinutesRemaining(0);
                    quota.setOverageVoiceMinutes(beforeOverage + (used - beforeRemaining));
                }
                checkAndPublishThreshold(quota, "VOICE",
                        beforeRemaining, quota.getMinutesRemaining(), totalIncluded);
                publishUsageAggregatedIfOverage(quota, beforeOverage, quota.getOverageVoiceMinutes());
            }
            case SMS -> {
                beforeRemaining = quota.getSmsRemaining();
                totalIncluded = quota.getSmsTotal() != null ? quota.getSmsTotal() : beforeRemaining + used;
                beforeOverage = quota.getOverageSms();
                quota.setSmsUsed(quota.getSmsUsed() + used);
                if (used <= beforeRemaining) {
                    quota.setSmsRemaining(beforeRemaining - used);
                } else {
                    quota.setSmsRemaining(0);
                    quota.setOverageSms(beforeOverage + (used - beforeRemaining));
                }
                checkAndPublishThreshold(quota, "SMS",
                        beforeRemaining, quota.getSmsRemaining(), totalIncluded);
                publishUsageAggregatedIfOverage(quota, beforeOverage, quota.getOverageSms());
            }
            case DATA -> {
                beforeRemaining = quota.getMbRemaining();
                totalIncluded = quota.getMbTotal() != null ? quota.getMbTotal() : beforeRemaining + used;
                beforeOverage = quota.getOverageDataMb();
                quota.setDataMbUsed(quota.getDataMbUsed() + used);
                if (used <= beforeRemaining) {
                    quota.setMbRemaining(beforeRemaining - used);
                } else {
                    quota.setMbRemaining(0);
                    quota.setOverageDataMb(beforeOverage + (used - beforeRemaining));
                }
                checkAndPublishThreshold(quota, "DATA",
                        beforeRemaining, quota.getMbRemaining(), totalIncluded);
                publishUsageAggregatedIfOverage(quota, beforeOverage, quota.getOverageDataMb());
            }
        }
        quotaRepository.save(quota);
    }

    private void publishUsageAggregatedIfOverage(Quota quota, int beforeOverage, int afterOverage) {
        if (afterOverage <= beforeOverage) {
            return;
        }

        UsageAggregatedEvent event = new UsageAggregatedEvent(
                UUID.randomUUID(),
                USAGE_AGGREGATED_EVENT,
                quota.getSubscriptionId(),
                quota.getCustomerId(),
                quota.getPeriodStart(),
                quota.getPeriodEnd(),
                quota.getVoiceUsed(),
                quota.getSmsUsed(),
                quota.getDataMbUsed(),
                quota.getOverageVoiceMinutes(),
                quota.getOverageSms(),
                quota.getOverageDataMb()
        );
        outboxEventWriter.write(quota.getSubscriptionId(), QUOTA_AGGREGATE_TYPE, USAGE_AGGREGATED_EVENT, event);
        log.info("UsageAggregated published for subscriptionId={}", quota.getSubscriptionId());
    }

    private void checkAndPublishThreshold(Quota quota, String type,
                                           int before, int after, int total) {
        if (total == 0) return;

        UUID subscriptionId = quota.getSubscriptionId();
        double usedPercent = ((double)(total - after) / total) * 100;
        double beforePercent = ((double)(total - before) / total) * 100;

        // %100 aşıldı mı?
        if (after == 0 && before > 0) {
            QuotaThresholdEvent event = new QuotaThresholdEvent(
                    UUID.randomUUID(), QUOTA_EXCEEDED_EVENT, quota.getCustomerId(),
                    subscriptionId, type, "PERCENT_100", 0);
            outboxEventWriter.write(subscriptionId, QUOTA_AGGREGATE_TYPE, QUOTA_EXCEEDED_EVENT, event);
            log.warn("Quota EXCEEDED for subscription: {} type: {}", subscriptionId, type);
        }
        // %80 geçildi mi? (önceden geçilmemişse)
        else if (usedPercent >= 80 && beforePercent < 80) {
            QuotaThresholdEvent event = new QuotaThresholdEvent(
                    UUID.randomUUID(), QUOTA_THRESHOLD_REACHED_EVENT, quota.getCustomerId(),
                    subscriptionId, type, "PERCENT_80", after);
            outboxEventWriter.write(subscriptionId, QUOTA_AGGREGATE_TYPE, QUOTA_THRESHOLD_REACHED_EVENT, event);
            log.info("Quota 80% reached for subscription: {} type: {}", subscriptionId, type);
        }
    }

    private UsageRecordResponse toResponse(UsageRecord r) {
        return new UsageRecordResponse(r.getId(), r.getSubscriptionId(),
            r.getType(), r.getQuantity(), r.getRecordedAt(), r.getCdrRef());
    }

    private QuotaResponse toQuotaResponse(Quota q) {
        return new QuotaResponse(q.getId(), q.getSubscriptionId(),
            q.getPeriodStart(), q.getPeriodEnd(),
            q.getMinutesRemaining(), q.getSmsRemaining(), q.getMbRemaining());
    }
}