package com.turkcell.usage_service.simulator;

import com.turkcell.usage_service.domain.entity.Quota;
import com.turkcell.usage_service.domain.entity.UsageRecord;
import com.turkcell.usage_service.dto.request.CreateUsageRecordRequest;
import com.turkcell.usage_service.repository.QuotaRepository;
import com.turkcell.usage_service.service.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * CDR (Call Detail Record) simulatoru: aktif kotasi olan rastgele bir abonelik icin
 * rastgele VOICE/SMS/DATA kullanimi uretir (Senaryo 3 - kota asimi akisinin test edilmesi).
 * Varsayilan olarak kapalidir; usage.cdr-simulator.enabled=true ile acilir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "usage.cdr-simulator", name = "enabled", havingValue = "true")
public class CdrSimulator {

    private static final int MAX_VOICE_MINUTES = 60;
    private static final int MAX_SMS_COUNT = 20;
    private static final int MAX_DATA_MB = 500;

    private final QuotaRepository quotaRepository;
    private final UsageService usageService;
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

        usageService.recordUsage(new CreateUsageRecordRequest(
                quota.getSubscriptionId(), type, quantity, cdrRef));

        log.info("CDR simulator generated usage: subscriptionId={}, type={}, quantity={}, cdrRef={}",
                quota.getSubscriptionId(), type, quantity, cdrRef);
    }

    private int randomQuantity(UsageRecord.UsageType type) {
        return switch (type) {
            case VOICE -> random.nextInt(MAX_VOICE_MINUTES) + 1;
            case SMS -> random.nextInt(MAX_SMS_COUNT) + 1;
            case DATA -> random.nextInt(MAX_DATA_MB) + 1;
        };
    }
}
