package com.turkcell.usage_service.service.impl;

import com.turkcell.usage_service.domain.entity.Quota;
import com.turkcell.usage_service.domain.entity.UsageRecord;
import com.turkcell.usage_service.dto.request.CreateUsageRecordRequest;
import com.turkcell.usage_service.event.QuotaThresholdEvent;
import com.turkcell.usage_service.outbox.service.OutboxEventWriter;
import com.turkcell.usage_service.repository.QuotaRepository;
import com.turkcell.usage_service.repository.UsageRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageServiceImplTest {

    @Mock
    private UsageRecordRepository usageRecordRepository;

    @Mock
    private QuotaRepository quotaRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @InjectMocks
    private UsageServiceImpl usageService;

    @Captor
    private ArgumentCaptor<Object> outboxPayloadCaptor;

    private UUID subscriptionId;
    private UUID customerId;
    private Quota quota;

    @BeforeEach
    void setUp() {
        subscriptionId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        quota = new Quota();
        quota.setSubscriptionId(subscriptionId);
        quota.setCustomerId(customerId);
        quota.setPeriodStart(LocalDate.now().minusDays(5));
        quota.setPeriodEnd(LocalDate.now().plusDays(25));
        quota.setMinutesTotal(100);
        quota.setSmsTotal(100);
        quota.setMbTotal(100);
        quota.setMinutesRemaining(100);
        quota.setSmsRemaining(100);
        quota.setMbRemaining(100);
    }

    private void stubActiveQuota() {
        when(quotaRepository.findBySubscriptionIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                eq(subscriptionId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Optional.of(quota));
    }

    // --- Happy path ---

    @Test
    void recordUsage_shouldDecrementQuota_withoutThresholdEvent() {
        stubActiveQuota();

        usageService.recordUsage(new CreateUsageRecordRequest(
                subscriptionId, UsageRecord.UsageType.VOICE, new BigDecimal("10"), "CDR-1"));

        verify(usageRecordRepository).save(any(UsageRecord.class));
        assertThat(quota.getMinutesRemaining()).isEqualTo(90);
        verify(outboxEventWriter, never()).write(any(), anyString(), anyString(), any());
    }

    // --- Esik / kota asimi ---

    @Test
    void recordUsage_shouldWriteQuotaExceededOutbox_whenQuotaDepleted() {
        stubActiveQuota();

        usageService.recordUsage(new CreateUsageRecordRequest(
                subscriptionId, UsageRecord.UsageType.DATA, new BigDecimal("100"), "CDR-2"));

        assertThat(quota.getMbRemaining()).isZero();
        verify(outboxEventWriter).write(eq(subscriptionId), eq("Quota"), eq("QuotaExceeded"),
                outboxPayloadCaptor.capture());
        QuotaThresholdEvent event = (QuotaThresholdEvent) outboxPayloadCaptor.getValue();
        assertThat(event.getCustomerId()).isEqualTo(customerId);
        assertThat(event.getThreshold()).isEqualTo("PERCENT_100");
        assertThat(event.getType()).isEqualTo("DATA");
        assertThat(event.getRemaining()).isZero();
    }

    @Test
    void recordUsage_shouldWriteThresholdReachedOutbox_when80PercentUsed() {
        stubActiveQuota();

        usageService.recordUsage(new CreateUsageRecordRequest(
                subscriptionId, UsageRecord.UsageType.SMS, new BigDecimal("80"), "CDR-3"));

        assertThat(quota.getSmsRemaining()).isEqualTo(20);
        verify(outboxEventWriter).write(eq(subscriptionId), eq("Quota"), eq("QuotaThresholdReached"),
                outboxPayloadCaptor.capture());
        QuotaThresholdEvent event = (QuotaThresholdEvent) outboxPayloadCaptor.getValue();
        assertThat(event.getCustomerId()).isEqualTo(customerId);
        assertThat(event.getThreshold()).isEqualTo("PERCENT_80");
        assertThat(event.getType()).isEqualTo("SMS");
    }

    // --- Basarisiz islem ---

    @Test
    void getActiveQuota_shouldThrow_whenNoActiveQuota() {
        when(quotaRepository.findBySubscriptionIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                eq(subscriptionId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> usageService.getActiveQuota(subscriptionId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Active quota not found");
    }
}
