package com.turkcell.notification_service.messaging;

import com.turkcell.notification_service.domain.entity.ProcessedEvent;
import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.event.InvoiceGeneratedEvent;
import com.turkcell.notification_service.repository.ProcessedEventRepository;
import com.turkcell.notification_service.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private BillingEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BillingEventConsumer(notificationService, processedEventRepository);
    }

    @Test
    void onInvoiceGenerated_shouldSendInvoiceNotificationAndMarkEventProcessed() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        InvoiceGeneratedEvent event = new InvoiceGeneratedEvent(
                eventId, "InvoiceGenerated", UUID.randomUUID(), customerId, UUID.randomUUID(),
                new BigDecimal("250.00"), "TRY", LocalDate.of(2026, 8, 1), LocalDateTime.now());

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

        consumer.onInvoiceGenerated(event);

        ArgumentCaptor<SendNotificationRequest> captor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(customerId);
        assertThat(captor.getValue().templateCode()).isEqualTo("INVOICE_GENERATED");
        assertThat(captor.getValue().placeholders()).containsEntry("amount", "250.00");
        assertThat(captor.getValue().placeholders()).containsEntry("currency", "TRY");
        assertThat(captor.getValue().placeholders()).containsEntry("dueDate", "2026-08-01");

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void onInvoiceGenerated_shouldSkipWhenEventAlreadyProcessed() {
        UUID eventId = UUID.randomUUID();
        InvoiceGeneratedEvent event = new InvoiceGeneratedEvent(
                eventId, "InvoiceGenerated", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("250.00"), "TRY", LocalDate.of(2026, 8, 1), LocalDateTime.now());

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(true);

        consumer.onInvoiceGenerated(event);

        verify(notificationService, never()).send(any());
        verify(processedEventRepository, never()).save(any());
    }
}
