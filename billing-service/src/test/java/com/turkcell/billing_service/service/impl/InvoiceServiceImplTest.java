package com.turkcell.billing_service.service.impl;

import com.turkcell.billing_service.domain.entity.Invoice;
import com.turkcell.billing_service.event.PaymentCompletedEvent;
import com.turkcell.billing_service.kafka.entity.ProcessedEvent;
import com.turkcell.billing_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.billing_service.outbox.service.OutboxEventWriter;
import com.turkcell.billing_service.repository.InvoiceLineRepository;
import com.turkcell.billing_service.repository.InvoiceRepository;
import com.turkcell.billing_service.service.InvoicePdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceLineRepository invoiceLineRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Mock
    private InvoicePdfService invoicePdfService;

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    private UUID eventId;
    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
    }

    private PaymentCompletedEvent paymentCompletedEvent(UUID invoiceId, String status) {
        return new PaymentCompletedEvent(
                eventId, "PaymentCompleted", UUID.randomUUID(), null, invoiceId, UUID.randomUUID(),
                new BigDecimal("100.00"), "TRY", status, LocalDateTime.now(),
                null, null, null, null);
    }

    // --- Happy path ---

    @Test
    void handlePaymentCompleted_shouldMarkInvoicePaid_whenStatusCompleted() {
        Invoice invoice = new Invoice();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        invoiceService.handlePaymentCompleted(paymentCompletedEvent(invoiceId, "COMPLETED"));

        assertThat(invoice.getStatus()).isEqualTo("PAID");
        verify(invoiceRepository).save(invoice);
        verify(outboxEventWriter).write(any(), eq("Invoice"), eq("InvoicePaid"), any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    // --- Basarisiz islem ---

    @Test
    void handlePaymentCompleted_shouldMarkInvoiceOverdue_whenStatusFailed() {
        Invoice invoice = new Invoice();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        invoiceService.handlePaymentCompleted(paymentCompletedEvent(invoiceId, "FAILED"));

        assertThat(invoice.getStatus()).isEqualTo("OVERDUE");
        verify(invoiceRepository).save(invoice);
        verify(outboxEventWriter).write(any(), eq("Invoice"), eq("InvoiceOverdue"), any());
    }

    // --- Idempotency ---

    @Test
    void handlePaymentCompleted_shouldSkip_whenEventAlreadyProcessed() {
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        invoiceService.handlePaymentCompleted(paymentCompletedEvent(invoiceId, "COMPLETED"));

        verify(invoiceRepository, never()).findById(any());
        verify(invoiceRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void handlePaymentCompleted_shouldOnlyMarkProcessed_whenInvoiceIdIsNull() {
        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        invoiceService.handlePaymentCompleted(paymentCompletedEvent(null, "COMPLETED"));

        verify(invoiceRepository, never()).findById(any());
        verify(invoiceRepository, never()).save(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void getById_shouldThrow_whenInvoiceNotFound() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getById(invoiceId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }
}
