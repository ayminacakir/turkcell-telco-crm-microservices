package com.turkcell.usage_service.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.turkcell.usage_service.domain.entity.Quota;
import com.turkcell.usage_service.domain.entity.UsageRecord;
import com.turkcell.usage_service.dto.request.CreateUsageRecordRequest;
import com.turkcell.usage_service.event.QuotaThresholdEvent;
import com.turkcell.usage_service.repository.QuotaRepository;
import com.turkcell.usage_service.repository.UsageRecordRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageRecordService {

    private final UsageRecordRepository usageRecordRepository;
    private final QuotaRepository quotaRepository;
    private final KafkaTemplate<String, QuotaThresholdEvent> kafkaTemplate;

    private static final String QUOTA_THRESHOLD_TOPIC = "quota.threshold";

    @Transactional
    public void createUsageRecord(CreateUsageRecordRequest request) {
        UsageRecord record = new UsageRecord();
        record.setSubscriptionId(request.subscriptionId());
        record.setType(request.type());
        record.setQuantity(request.quantity());
        record.setRecordedAt(LocalDateTime.now());
        record.setCdrRef(request.cdrRef());
        usageRecordRepository.save(record);

       Quota quota = quotaRepository.findFirstBySubscriptionIdOrderByPeriodStartDesc(request.subscriptionId())
        .orElseThrow(() -> new RuntimeException("Quota not found for subscriptionId: " + request.subscriptionId()));

        int used = request.quantity().intValue();
        int includedTotal;
        int remainingBefore;

        switch (request.type()) {
            case VOICE -> {
                includedTotal = quota.getMinutesRemaining(); // not: bu "kalan", "toplam" değil - aşağıda not var
                remainingBefore = quota.getMinutesRemaining();
                quota.setMinutesRemaining(Math.max(0, quota.getMinutesRemaining() - used));
                checkThreshold(quota.getSubscriptionId(), "VOICE", remainingBefore, quota.getMinutesRemaining());
            }
            case SMS -> {
                remainingBefore = quota.getSmsRemaining();
                quota.setSmsRemaining(Math.max(0, quota.getSmsRemaining() - used));
                checkThreshold(quota.getSubscriptionId(), "SMS", remainingBefore, quota.getSmsRemaining());
            }
            case DATA -> {
                remainingBefore = quota.getMbRemaining();
                quota.setMbRemaining(Math.max(0, quota.getMbRemaining() - used));
                checkThreshold(quota.getSubscriptionId(), "DATA", remainingBefore, quota.getMbRemaining());
            }
        }

        quotaRepository.save(quota);
        log.info("Usage recorded: subscriptionId={}, type={}, quantity={}", 
                request.subscriptionId(), request.type(), request.quantity());
    }

    private void checkThreshold(UUID subscriptionId, String type, int remainingBefore, int remainingAfter) {
        // not: "included total"'a ihtiyacımız var yüzde hesaplamak için - bkz. aşağıdaki not
    }
}