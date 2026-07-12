package com.turkcell.notification_service.messaging;

import com.turkcell.notification_service.domain.entity.ProcessedEvent;
import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.event.CustomerRegisteredEvent;
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
class CustomerEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private CustomerEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CustomerEventConsumer(notificationService, processedEventRepository);
    }

    @Test
    void onCustomerRegistered_shouldSendWelcomeNotificationAndMarkEventProcessed() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        CustomerRegisteredEvent event = new CustomerRegisteredEvent(
                eventId, "CustomerRegistered", customerId, "Ahmet Yilmaz",
                "ahmet@example.com", "5559876543", LocalDateTime.now());

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

        consumer.onCustomerRegistered(event);

        ArgumentCaptor<SendNotificationRequest> captor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(customerId);
        assertThat(captor.getValue().templateCode()).isEqualTo("CUSTOMER_WELCOME");
        assertThat(captor.getValue().placeholders()).containsEntry("fullName", "Ahmet Yilmaz");

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void onCustomerRegistered_shouldSkipWhenEventAlreadyProcessed() {
        UUID eventId = UUID.randomUUID();
        CustomerRegisteredEvent event = new CustomerRegisteredEvent(
                eventId, "CustomerRegistered", UUID.randomUUID(), "Ahmet Yilmaz",
                "ahmet@example.com", "5559876543", LocalDateTime.now());

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(true);

        consumer.onCustomerRegistered(event);

        verify(notificationService, never()).send(any());
        verify(processedEventRepository, never()).save(any());
    }
}
