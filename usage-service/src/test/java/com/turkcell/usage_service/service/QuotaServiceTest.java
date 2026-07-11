package com.turkcell.usage_service.service;

import com.turkcell.usage_service.domain.entity.Quota;
import com.turkcell.usage_service.event.SubscriptionActivatedEvent;
import com.turkcell.usage_service.kafka.entity.ProcessedEvent;
import com.turkcell.usage_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.usage_service.repository.QuotaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock
    private QuotaRepository quotaRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private QuotaService quotaService;

    @Captor
    private ArgumentCaptor<Quota> quotaCaptor;

    private UUID eventId;
    private UUID subscriptionId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
    }

    private SubscriptionActivatedEvent subscriptionActivatedEvent() {
        return new SubscriptionActivatedEvent(
                eventId, "SubscriptionActivated", UUID.randomUUID(), subscriptionId, UUID.randomUUID(),
                "TARIFF-BASIC", "905551112233", 500, 250, 10240,
                LocalDateTime.of(2026, 7, 10, 12, 0));
    }

    // --- Happy path ---

    @Test
    void createQuotaForSubscription_shouldCreateQuotaFromEvent() {
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(quotaRepository.existsBySubscriptionId(subscriptionId)).thenReturn(false);

        quotaService.createQuotaForSubscription(subscriptionActivatedEvent());

        verify(quotaRepository).save(quotaCaptor.capture());
        Quota saved = quotaCaptor.getValue();
        assertThat(saved.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(saved.getPeriodStart()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(saved.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 8, 9));
        assertThat(saved.getMinutesRemaining()).isEqualTo(500);
        assertThat(saved.getSmsRemaining()).isEqualTo(250);
        assertThat(saved.getMbRemaining()).isEqualTo(10240);

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    // --- Idempotency ---

    @Test
    void createQuotaForSubscription_shouldSkip_whenEventAlreadyProcessed() {
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        quotaService.createQuotaForSubscription(subscriptionActivatedEvent());

        verify(quotaRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void createQuotaForSubscription_shouldNotDuplicate_whenQuotaExistsForSubscription() {
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(quotaRepository.existsBySubscriptionId(subscriptionId)).thenReturn(true);

        quotaService.createQuotaForSubscription(subscriptionActivatedEvent());

        verify(quotaRepository, never()).save(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }
}
