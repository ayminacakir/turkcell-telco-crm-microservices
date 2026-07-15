package com.turkcell.notification_service.messaging;

import com.turkcell.notification_service.domain.entity.ProcessedEvent;
import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.event.SubscriptionActivatedEvent;
import com.turkcell.notification_service.repository.ProcessedEventRepository;
import com.turkcell.notification_service.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private SubscriptionEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SubscriptionEventConsumer(notificationService, processedEventRepository);
    }

    @Test
    void onSubscriptionActivated_shouldSendWelcomeSmsAndMarkEventProcessed() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        SubscriptionActivatedEvent event = new SubscriptionActivatedEvent(
                eventId, "SubscriptionActivated", UUID.randomUUID(), UUID.randomUUID(),
                customerId, "POSTPAID_20GB", "5551234567", LocalDateTime.now());

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

        consumer.onSubscriptionActivated(event);

        ArgumentCaptor<SendNotificationRequest> captor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(customerId);
        assertThat(captor.getValue().templateCode()).isEqualTo("WELCOME_SMS");
        assertThat(captor.getValue().placeholders()).containsEntry("tariffCode", "POSTPAID_20GB");
        assertThat(captor.getValue().placeholders()).containsEntry("msisdn", "5551234567");

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void onSubscriptionActivated_shouldSkipWhenEventAlreadyProcessed() {
        UUID eventId = UUID.randomUUID();
        SubscriptionActivatedEvent event = new SubscriptionActivatedEvent(
                eventId, "SubscriptionActivated", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "POSTPAID_20GB", "5551234567", LocalDateTime.now());

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(true);

        consumer.onSubscriptionActivated(event);

        verify(notificationService, never()).send(any());
        verify(processedEventRepository, never()).save(any());
    }
}
