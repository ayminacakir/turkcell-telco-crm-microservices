package com.turkcell.subscription_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.subscription_service.entity.Subscription;
import com.turkcell.subscription_service.enums.MnpStatus;
import com.turkcell.subscription_service.enums.SubscriptionStatus;
import com.turkcell.subscription_service.exception.SubscriptionNotFoundException;
import com.turkcell.subscription_service.kafka.event.PaymentFailedEvent;
import com.turkcell.subscription_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.subscription_service.outbox.repository.OutboxEventRepository;
import com.turkcell.subscription_service.repository.MsisdnPoolRepository;
import com.turkcell.subscription_service.repository.SimCardRepository;
import com.turkcell.subscription_service.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private MsisdnPoolRepository msisdnPoolRepository;
    @Mock
    private SimCardRepository simCardRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private AuditLogService auditLogService;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                subscriptionRepository,
                msisdnPoolRepository,
                simCardRepository,
                outboxEventRepository,
                processedEventRepository,
                new ObjectMapper().findAndRegisterModules(),
                auditLogService);
    }

    @Test
    void suspend_withActiveSubscription_setsStatusSuspendedAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        Subscription sub = buildSubscription(id, SubscriptionStatus.ACTIVE);

        when(subscriptionRepository.findById(id)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = subscriptionService.suspend(id);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.SUSPENDED);
        verify(outboxEventRepository).save(any()); // SubscriptionSuspended outbox event
    }

    @Test
    void suspend_withNonActiveSubscription_throwsIllegalArgument() {
        UUID id = UUID.randomUUID();
        Subscription sub = buildSubscription(id, SubscriptionStatus.SUSPENDED);

        when(subscriptionRepository.findById(id)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> subscriptionService.suspend(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active subscription");

        verify(subscriptionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void suspend_withNonexistentId_throwsSubscriptionNotFoundException() {
        UUID id = UUID.randomUUID();
        when(subscriptionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.suspend(id))
                .isInstanceOf(SubscriptionNotFoundException.class);
    }

    @Test
    void reactivate_withSuspendedSubscription_setsStatusActive() {
        UUID id = UUID.randomUUID();
        Subscription sub = buildSubscription(id, SubscriptionStatus.SUSPENDED);

        when(subscriptionRepository.findById(id)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = subscriptionService.reactivate(id);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void reactivate_withActiveSubscription_throwsIllegalArgument() {
        UUID id = UUID.randomUUID();
        Subscription sub = buildSubscription(id, SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findById(id)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> subscriptionService.reactivate(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    void terminate_withActiveSubscription_setsStatusTerminatedAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        Subscription sub = buildSubscription(id, SubscriptionStatus.ACTIVE);

        when(subscriptionRepository.findById(id)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = subscriptionService.terminate(id);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.TERMINATED);
        verify(outboxEventRepository).save(any()); // SubscriptionTerminated outbox event
    }

    @Test
    void terminate_withAlreadyTerminatedSubscription_throwsIllegalArgument() {
        UUID id = UUID.randomUUID();
        Subscription sub = buildSubscription(id, SubscriptionStatus.TERMINATED);
        when(subscriptionRepository.findById(id)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> subscriptionService.terminate(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already terminated");
    }

    @Test
    void handlePaymentFailed_withActiveSubscription_suspendsAndPublishesEvent() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Subscription sub = buildSubscription(UUID.randomUUID(), SubscriptionStatus.ACTIVE);
        sub.setOrderId(orderId);

        PaymentFailedEvent event = buildPaymentFailedEvent(eventId, orderId);

        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(subscriptionRepository.findByOrderId(orderId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.handlePaymentFailed(event);

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        verify(outboxEventRepository).save(any());
        verify(processedEventRepository).save(any());
    }

    @Test
    void handlePaymentFailed_withAlreadySuspendedSubscription_doesNotSuspendAgain() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Subscription sub = buildSubscription(UUID.randomUUID(), SubscriptionStatus.SUSPENDED);
        sub.setOrderId(orderId);

        PaymentFailedEvent event = buildPaymentFailedEvent(eventId, orderId);

        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(subscriptionRepository.findByOrderId(orderId)).thenReturn(Optional.of(sub));

        subscriptionService.handlePaymentFailed(event);

        verify(subscriptionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
        verify(processedEventRepository).save(any()); // still marks processed
    }

    @Test
    void handlePaymentFailed_withAlreadyProcessedEvent_skips() {
        UUID eventId = UUID.randomUUID();
        PaymentFailedEvent event = buildPaymentFailedEvent(eventId, UUID.randomUUID());

        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        subscriptionService.handlePaymentFailed(event);

        verify(subscriptionRepository, never()).findByOrderId(any());
        verify(processedEventRepository, never()).save(any());
    }

    private Subscription buildSubscription(UUID id, SubscriptionStatus status) {
        Subscription s = new Subscription();
        try {
            java.lang.reflect.Field idField = Subscription.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(s, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        s.setCustomerId(UUID.randomUUID());
        s.setMsisdn("05301234567");
        s.setTariffCode("TARIFF_50");
        s.setStatus(status);
        s.setMnpStatus(MnpStatus.NOT_REQUESTED);
        s.setActivatedAt(LocalDateTime.now());
        return s;
    }

    private PaymentFailedEvent buildPaymentFailedEvent(UUID eventId, UUID orderId) {
        return new PaymentFailedEvent(
                eventId, "PaymentFailed", UUID.randomUUID(), orderId,
                UUID.randomUUID(), new BigDecimal("199.99"), "TRY", "FAILED",
                "Insufficient funds", LocalDateTime.now());
    }
}
