package com.turkcell.billing_service.service.impl;

import com.turkcell.billing_service.config.BillingProperties;
import com.turkcell.billing_service.domain.entity.BillCycle;
import com.turkcell.billing_service.event.SubscriptionActivatedEvent;
import com.turkcell.billing_service.kafka.entity.ProcessedEvent;
import com.turkcell.billing_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.billing_service.repository.BillCycleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// BeforeEach'teki ortak stub'lar her test metodunda kullanilmiyor;
// Mockito strict-stubs bunu hata sayip CI'yi kiriyordu (UnnecessaryStubbing).
@MockitoSettings(strictness = Strictness.LENIENT)
class BillCycleServiceImplTest {

    @Mock
    private BillCycleRepository billCycleRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private BillingProperties billingProperties;

    @InjectMocks
    private BillCycleServiceImpl billCycleService;

    @Captor
    private ArgumentCaptor<BillCycle> billCycleCaptor;

    private UUID eventId;
    private UUID customerId;
    private UUID subscriptionId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        when(billingProperties.resolveMonthlyFee(any())).thenReturn(new BigDecimal("149.90"));
    }

    private SubscriptionActivatedEvent subscriptionActivatedEvent() {
        return new SubscriptionActivatedEvent(
                eventId, "SubscriptionActivated", UUID.randomUUID(), subscriptionId, customerId,
                "TARIFF-BASIC", "905551112233", 500, 250, 10240,
                LocalDateTime.of(2026, 7, 10, 12, 0));
    }

    // --- Happy path ---

    @Test
    void createBillCycleForSubscription_shouldCreateBillCycle() {
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(billCycleRepository.existsByCustomerId(customerId)).thenReturn(false);

        billCycleService.createBillCycleForSubscription(subscriptionActivatedEvent());

        verify(billCycleRepository).save(billCycleCaptor.capture());
        BillCycle saved = billCycleCaptor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(saved.getDayOfMonth()).isEqualTo(10);
        assertThat(saved.getNextRunDate()).isEqualTo(java.time.LocalDate.of(2026, 8, 10));

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    // --- Idempotency ---

    @Test
    void createBillCycleForSubscription_shouldSkip_whenEventAlreadyProcessed() {
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        billCycleService.createBillCycleForSubscription(subscriptionActivatedEvent());

        verify(billCycleRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void createBillCycleForSubscription_shouldNotDuplicate_whenBillCycleExistsForCustomer() {
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(billCycleRepository.existsByCustomerId(customerId)).thenReturn(true);

        billCycleService.createBillCycleForSubscription(subscriptionActivatedEvent());

        verify(billCycleRepository, never()).save(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    // --- Basarisiz islem ---

    @Test
    void getById_shouldThrow_whenBillCycleNotFound() {
        UUID id = UUID.randomUUID();
        when(billCycleRepository.findById(id)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> billCycleService.getById(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }
}
