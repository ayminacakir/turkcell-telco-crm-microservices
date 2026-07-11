package com.turkcell.payment_service.service.impl;

import com.turkcell.payment_service.domain.entity.Payment;
import com.turkcell.payment_service.domain.entity.PaymentAttempt;
import com.turkcell.payment_service.dto.request.CreatePaymentRequest;
import com.turkcell.payment_service.dto.response.PaymentAttemptResponse;
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
import com.turkcell.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String PAYMENT_AGGREGATE_TYPE = "Payment";
    private static final String PAYMENT_COMPLETED_EVENT = "PaymentCompleted";
    private static final String PAYMENT_FAILED_EVENT = "PaymentFailed";
    private static final String ORDER_CREATED_EVENT = "OrderCreated";
    private static final String DEFAULT_CURRENCY = "TRY";
    private static final String FAILURE_REASON = "Insufficient funds";
    private static final int MAX_ATTEMPTS = 3;

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final AuditLogService auditLogService;
    private final PaymentGateway paymentGateway;

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        paymentRepository.findByInvoiceIdAndStatusNot(request.invoiceId(), "FAILED")
            .ifPresent(existing -> {
                throw new RuntimeException(
                    "Payment already exists for invoiceId: " + request.invoiceId()
                );
            });

        Payment payment = new Payment();
        payment.setInvoiceId(request.invoiceId());
        payment.setAmount(request.amount());
        payment.setMethod(request.method());
        payment.setStatus("PENDING");
        Payment saved = paymentRepository.save(payment);

        auditLogService.logPaymentAction(saved.getId(), "PAYMENT_CREATED",
                "invoiceId=" + saved.getInvoiceId() + ", amount=" + saved.getAmount());
        return toResponse(saved);
    }

    @Override
    public PaymentResponse getById(UUID id) {
        return toResponse(findPaymentById(id));
    }

    @Override
    public List<PaymentResponse> getByInvoiceId(UUID invoiceId) {
        return paymentRepository.findByInvoiceId(invoiceId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(UUID id) {
        Payment payment = findPaymentById(id);

        if ("COMPLETED".equals(payment.getStatus())) {
            throw new RuntimeException("Payment already completed: " + id);
        }

        int attemptNo = paymentAttemptRepository.countByPaymentId(id) + 1;
        boolean success = paymentGateway.charge(payment.getMethod(), payment.getAmount());

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(payment);
        attempt.setAttemptNo(attemptNo);
        attempt.setAttemptedAt(LocalDateTime.now());

        if (success) {
            payment.setStatus("COMPLETED");
            payment.setPaidAt(LocalDateTime.now());
            payment.setExternalRef(generateExternalRef());
            attempt.setResponse("{\"status\":\"SUCCESS\",\"externalRef\":\"" + payment.getExternalRef() + "\"}");

            paymentAttemptRepository.save(attempt);
            Payment saved = paymentRepository.save(payment);

            writePaymentCompletedToOutbox(saved, null, null, null, null, DEFAULT_CURRENCY);
            auditLogService.logPaymentAction(saved.getId(), "PAYMENT_COMPLETED",
                    "invoiceId=" + saved.getInvoiceId() + ", externalRef=" + saved.getExternalRef());

            log.info("Payment completed: {}", id);
            return toResponse(saved);
        }

        if (attemptNo >= MAX_ATTEMPTS) {
            payment.setStatus("FAILED");
            attempt.setResponse("{\"status\":\"FAILED\",\"reason\":\"" + FAILURE_REASON + "\",\"attemptNo\":" + attemptNo + "}");
            paymentAttemptRepository.save(attempt);
            Payment saved = paymentRepository.save(payment);

            writePaymentFailedToOutbox(saved, DEFAULT_CURRENCY);
            auditLogService.logPaymentAction(saved.getId(), "PAYMENT_FAILED",
                    "invoiceId=" + saved.getInvoiceId() + ", reason=" + FAILURE_REASON + ", attemptNo=" + attemptNo);
            return toResponse(saved);
        }

        attempt.setResponse("{\"status\":\"FAILED\",\"reason\":\"" + FAILURE_REASON + "\",\"attemptNo\":" + attemptNo + "}");
        paymentAttemptRepository.save(attempt);
        auditLogService.logPaymentAction(payment.getId(), "PAYMENT_ATTEMPT_FAILED",
                "invoiceId=" + payment.getInvoiceId() + ", attemptNo=" + attemptNo);
        return toResponse(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(UUID id) {
        Payment payment = findPaymentById(id);

        if (!"COMPLETED".equals(payment.getStatus())) {
            throw new RuntimeException("Only completed payments can be refunded");
        }

        payment.setStatus("REFUNDED");
        Payment saved = paymentRepository.save(payment);
        auditLogService.logPaymentAction(saved.getId(), "PAYMENT_REFUNDED",
                "invoiceId=" + saved.getInvoiceId() + ", amount=" + saved.getAmount());
        log.info("Payment refunded: {}", id);
        return toResponse(saved);
    }

    @Override
    public List<PaymentAttemptResponse> getAttempts(UUID paymentId) {
        return paymentAttemptRepository.findByPaymentIdOrderByAttemptNoAsc(paymentId)
                .stream().map(a -> new PaymentAttemptResponse(
                        a.getId(),
                        a.getPayment().getId(),
                        a.getAttemptNo(),
                        a.getResponse(),
                        a.getAttemptedAt()
                )).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (isAlreadyProcessed(event.eventId())) {
            log.warn("OrderCreated event {} already processed, skipping", event.eventId());
            return;
        }

        if (paymentRepository.findByOrderId(event.orderId()).isPresent()) {
            log.warn("Payment already exists for orderId: {}, skipping", event.orderId());
            markProcessed(event.eventId());
            return;
        }

        Payment payment = new Payment();
        payment.setOrderId(event.orderId());
        payment.setCustomerId(event.customerId());
        payment.setAmount(event.totalAmount());
        payment.setMethod("CREDIT_CARD");
        payment.setStatus("PENDING");
        Payment saved = paymentRepository.save(payment);

        processOrderPayment(saved, event);
        markProcessed(event.eventId());
    }

    private void processOrderPayment(Payment payment, OrderCreatedEvent orderEvent) {
        boolean success = paymentGateway.charge(payment.getMethod(), payment.getAmount());

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(payment);
        attempt.setAttemptNo(1);
        attempt.setAttemptedAt(LocalDateTime.now());

        OrderCreatedItemEvent tariffItem = orderEvent.items() == null ? null : orderEvent.items().stream()
                .filter(item -> "TARIFF".equals(item.productType()))
                .findFirst()
                .orElse(null);

        String tariffCode = tariffItem != null ? tariffItem.productCode() : null;
        Integer minutesIncluded = tariffItem != null ? tariffItem.minutesIncluded() : null;
        Integer smsIncluded = tariffItem != null ? tariffItem.smsIncluded() : null;
        Integer dataMbIncluded = tariffItem != null ? tariffItem.dataMbIncluded() : null;
        String currency = orderEvent.currency() != null ? orderEvent.currency() : DEFAULT_CURRENCY;

        if (success) {
            payment.setStatus("COMPLETED");
            payment.setPaidAt(LocalDateTime.now());
            payment.setExternalRef(generateExternalRef());
            attempt.setResponse("{\"status\":\"SUCCESS\",\"externalRef\":\"" + payment.getExternalRef() + "\"}");
            paymentAttemptRepository.save(attempt);
            paymentRepository.save(payment);

            writePaymentCompletedToOutbox(payment, tariffCode, minutesIncluded, smsIncluded, dataMbIncluded, currency);
            auditLogService.logPaymentAction(payment.getId(), "PAYMENT_COMPLETED",
                    "orderId=" + payment.getOrderId() + ", tariffCode=" + tariffCode
                            + ", amount=" + payment.getAmount());
            log.info("Order payment completed: orderId={}, tariffCode={}", orderEvent.orderId(), tariffCode);
        } else {
            payment.setStatus("FAILED");
            attempt.setResponse("{\"status\":\"FAILED\",\"reason\":\"" + FAILURE_REASON + "\"}");
            paymentAttemptRepository.save(attempt);
            paymentRepository.save(payment);

            writePaymentFailedToOutbox(payment, currency);
            auditLogService.logPaymentAction(payment.getId(), "PAYMENT_FAILED",
                    "orderId=" + payment.getOrderId() + ", reason=" + FAILURE_REASON);
            log.info("Order payment failed: orderId={}", orderEvent.orderId());
        }
    }

    private void writePaymentCompletedToOutbox(Payment payment, String tariffCode, Integer minutesIncluded,
                                               Integer smsIncluded, Integer dataMbIncluded, String currency) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID(),
                PAYMENT_COMPLETED_EVENT,
                payment.getId(),
                payment.getOrderId(),
                payment.getInvoiceId(),
                payment.getCustomerId(),
                payment.getAmount(),
                currency,
                "COMPLETED",
                payment.getPaidAt(),
                tariffCode,
                minutesIncluded,
                smsIncluded,
                dataMbIncluded
        );
        UUID aggregateId = payment.getOrderId() != null ? payment.getOrderId() : payment.getId();
        outboxEventWriter.write(aggregateId, PAYMENT_AGGREGATE_TYPE, PAYMENT_COMPLETED_EVENT, event);
    }

    private void writePaymentFailedToOutbox(Payment payment, String currency) {
        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID(),
                PAYMENT_FAILED_EVENT,
                payment.getId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                currency,
                "FAILED",
                FAILURE_REASON,
                LocalDateTime.now()
        );
        UUID aggregateId = payment.getOrderId() != null ? payment.getOrderId() : payment.getId();
        outboxEventWriter.write(aggregateId, PAYMENT_AGGREGATE_TYPE, PAYMENT_FAILED_EVENT, event);
    }

    private boolean isAlreadyProcessed(UUID eventId) {
        return eventId != null && processedEventRepository.existsById(eventId);
    }

    private void markProcessed(UUID eventId) {
        if (eventId != null) {
            processedEventRepository.save(new ProcessedEvent(eventId, ORDER_CREATED_EVENT));
        }
    }

    private String generateExternalRef() {
        return "PSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Payment findPaymentById(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + id));
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getInvoiceId(), p.getAmount(),
                p.getMethod(), p.getStatus(), p.getExternalRef(), p.getPaidAt()
        );
    }
}
