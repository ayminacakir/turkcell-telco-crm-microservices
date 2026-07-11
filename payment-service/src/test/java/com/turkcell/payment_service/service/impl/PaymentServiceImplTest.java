package com.turkcell.payment_service.service.impl;

import com.turkcell.payment_service.domain.entity.Payment;
import com.turkcell.payment_service.domain.entity.PaymentAttempt;
import com.turkcell.payment_service.dto.request.CreatePaymentRequest;
import com.turkcell.payment_service.dto.response.PaymentResponse;
import com.turkcell.payment_service.event.OrderCreatedEvent;
import com.turkcell.payment_service.event.OrderCreatedItemEvent;
import com.turkcell.payment_service.event.PaymentCompletedEvent;
import com.turkcell.payment_service.event.PaymentFailedEvent;
import com.turkcell.payment_service.gateway.PaymentGateway;
import com.turkcell.payment_service.kafka.entity.ProcessedEvent;
import com.turkcell.payment_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.payment_service.outbox.service.OutboxEventWriter;
import com.turkcell.payment_service.repository.PaymentAttemptRepository;
import com.turkcell.payment_service.repository.PaymentRepository;
import com.turkcell.payment_service.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Captor
    private ArgumentCaptor<Object> outboxPayloadCaptor;

    private UUID eventId;
    private UUID orderId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
    }

    private OrderCreatedEvent orderCreatedEvent() {
        OrderCreatedItemEvent tariffItem = new OrderCreatedItemEvent(
                UUID.randomUUID(), "TARIFF-BASIC", "TARIFF", "Basic Tariff",
                1, new BigDecimal("149.90"), new BigDecimal("149.90"),
                500, 250, 10240);
        return new OrderCreatedEvent(
                eventId, "OrderCreated", orderId, customerId,
                new BigDecimal("149.90"), "TRY", List.of(tariffItem), LocalDateTime.now());
    }

    // --- Happy path ---

    @Test
    void handleOrderCreated_shouldCompletePaymentAndWriteCompletedOutbox_whenGatewaySucceeds() {
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.charge(anyString(), any(BigDecimal.class))).thenReturn(true);

        paymentService.handleOrderCreated(orderCreatedEvent());

        verify(outboxEventWriter).write(eq(orderId), eq("Payment"), eq("PaymentCompleted"),
                outboxPayloadCaptor.capture());
        PaymentCompletedEvent published = (PaymentCompletedEvent) outboxPayloadCaptor.getValue();
        assertThat(published.getOrderId()).isEqualTo(orderId);
        assertThat(published.getCustomerId()).isEqualTo(customerId);
        assertThat(published.getAmount()).isEqualByComparingTo("149.90");
        assertThat(published.getCurrency()).isEqualTo("TRY");
        assertThat(published.getTariffCode()).isEqualTo("TARIFF-BASIC");
        assertThat(published.getMinutesIncluded()).isEqualTo(500);
        assertThat(published.getSmsIncluded()).isEqualTo(250);
        assertThat(published.getDataMbIncluded()).isEqualTo(10240);

        verify(auditLogService).logPaymentAction(any(), eq("PAYMENT_COMPLETED"), anyString());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    // --- Basarisiz islem ---

    @Test
    void handleOrderCreated_shouldFailPaymentAndWriteFailedOutbox_whenGatewayFails() {
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.charge(anyString(), any(BigDecimal.class))).thenReturn(false);

        paymentService.handleOrderCreated(orderCreatedEvent());

        verify(outboxEventWriter).write(eq(orderId), eq("Payment"), eq("PaymentFailed"),
                outboxPayloadCaptor.capture());
        PaymentFailedEvent published = (PaymentFailedEvent) outboxPayloadCaptor.getValue();
        assertThat(published.getOrderId()).isEqualTo(orderId);
        assertThat(published.getStatus()).isEqualTo("FAILED");

        verify(auditLogService).logPaymentAction(any(), eq("PAYMENT_FAILED"), anyString());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    // --- Idempotency ---

    @Test
    void handleOrderCreated_shouldSkip_whenEventAlreadyProcessed() {
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        paymentService.handleOrderCreated(orderCreatedEvent());

        verify(paymentRepository, never()).save(any());
        verify(outboxEventWriter, never()).write(any(), anyString(), anyString(), any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void handleOrderCreated_shouldSkipButMarkProcessed_whenPaymentAlreadyExistsForOrder() {
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(new Payment()));

        paymentService.handleOrderCreated(orderCreatedEvent());

        verify(paymentRepository, never()).save(any());
        verify(outboxEventWriter, never()).write(any(), anyString(), anyString(), any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    // --- REST akisi ---

    @Test
    void processPayment_shouldComplete_whenGatewaySucceeds() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = pendingInvoicePayment(paymentId);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentAttemptRepository.countByPaymentId(paymentId)).thenReturn(0);
        when(paymentGateway.charge(anyString(), any(BigDecimal.class))).thenReturn(true);

        PaymentResponse response = paymentService.processPayment(paymentId);

        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(paymentAttemptRepository).save(any(PaymentAttempt.class));
        verify(outboxEventWriter).write(any(), eq("Payment"), eq("PaymentCompleted"), any());
        verify(auditLogService).logPaymentAction(any(), eq("PAYMENT_COMPLETED"), anyString());
    }

    @Test
    void processPayment_shouldFailAndWriteFailedOutbox_onThirdFailedAttempt() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = pendingInvoicePayment(paymentId);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentAttemptRepository.countByPaymentId(paymentId)).thenReturn(2);
        when(paymentGateway.charge(anyString(), any(BigDecimal.class))).thenReturn(false);

        PaymentResponse response = paymentService.processPayment(paymentId);

        assertThat(response.status()).isEqualTo("FAILED");
        verify(outboxEventWriter).write(any(), eq("Payment"), eq("PaymentFailed"), any());
        verify(auditLogService).logPaymentAction(any(), eq("PAYMENT_FAILED"), anyString());
    }

    @Test
    void processPayment_shouldThrow_whenAlreadyCompleted() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = pendingInvoicePayment(paymentId);
        payment.setStatus("COMPLETED");
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.processPayment(paymentId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already completed");
        verify(outboxEventWriter, never()).write(any(), anyString(), anyString(), any());
    }

    @Test
    void createPayment_shouldThrow_whenActivePaymentExistsForInvoice() {
        UUID invoiceId = UUID.randomUUID();
        when(paymentRepository.findByInvoiceIdAndStatusNot(invoiceId, "FAILED"))
                .thenReturn(Optional.of(new Payment()));

        CreatePaymentRequest request = new CreatePaymentRequest(invoiceId, new BigDecimal("100.00"), "CREDIT_CARD");

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refundPayment_shouldThrow_whenPaymentNotCompleted() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = pendingInvoicePayment(paymentId);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refundPayment(paymentId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Only completed payments");
    }

    private Payment pendingInvoicePayment(UUID paymentId) {
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setInvoiceId(UUID.randomUUID());
        payment.setAmount(new BigDecimal("100.00"));
        payment.setMethod("CREDIT_CARD");
        payment.setStatus("PENDING");
        return payment;
    }
}
