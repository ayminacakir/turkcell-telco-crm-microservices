package com.turkcell.payment_service.service.impl;

import com.turkcell.payment_service.config.PaymentRetryProperties;
import com.turkcell.payment_service.domain.entity.Payment;
import com.turkcell.payment_service.domain.entity.PaymentAttempt;
import com.turkcell.payment_service.dto.request.CreatePaymentRequest;
import com.turkcell.payment_service.dto.response.PaymentAttemptResponse;
import com.turkcell.payment_service.dto.response.PaymentResponse;
import com.turkcell.payment_service.event.InvoiceGeneratedEvent;
import com.turkcell.payment_service.event.OrderCreatedEvent;
import com.turkcell.payment_service.event.OrderCreatedItemEvent;
import com.turkcell.payment_service.event.PaymentCompletedEvent;
import com.turkcell.payment_service.event.PaymentFailedEvent;
import com.turkcell.payment_service.event.PaymentRefundedEvent;
import com.turkcell.payment_service.gateway.PaymentGateway;
import com.turkcell.payment_service.kafka.entity.ProcessedEvent;
import com.turkcell.payment_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.payment_service.outbox.service.OutboxEventWriter;
import com.turkcell.payment_service.repository.PaymentAttemptRepository;
import com.turkcell.payment_service.repository.PaymentRepository;
import com.turkcell.payment_service.service.AuditLogService;
import com.turkcell.payment_service.service.PaymentService;
import com.turkcell.payment_service.service.WalletService;
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
    private static final String PAYMENT_REFUNDED_EVENT = "PaymentRefunded";
    private static final String ORDER_CREATED_EVENT = "OrderCreated";
    private static final String INVOICE_GENERATED_EVENT = "InvoiceGenerated";
    private static final String DEFAULT_CURRENCY = "TRY";
    private static final String FAILURE_REASON = "Insufficient funds";
    private static final String WALLET_METHOD = "WALLET";

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final AuditLogService auditLogService;
    private final PaymentGateway paymentGateway;
    private final WalletService walletService;
    private final PaymentRetryProperties paymentRetryProperties;

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, UUID idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = paymentRepository.findByPaymentRequestId(idempotencyKey);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

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
        payment.setPaymentRequestId(idempotencyKey);
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
        if ("REFUNDED".equals(payment.getStatus())) {
            throw new RuntimeException("Payment already refunded: " + id);
        }

        int attemptNo = paymentAttemptRepository.countByPaymentId(id) + 1;
        boolean success = chargePayment(payment);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(payment);
        attempt.setAttemptNo(attemptNo);
        attempt.setAttemptedAt(LocalDateTime.now());

        if (success) {
            payment.setStatus("COMPLETED");
            payment.setPaidAt(LocalDateTime.now());
            payment.setExternalRef(generateExternalRef());
            payment.setNextRetryAt(null);
            attempt.setResponse("{\"status\":\"SUCCESS\",\"externalRef\":\"" + payment.getExternalRef() + "\"}");

            paymentAttemptRepository.save(attempt);
            Payment saved = paymentRepository.save(payment);

            writePaymentCompletedToOutbox(saved, null, null, null, null, DEFAULT_CURRENCY);
            auditLogService.logPaymentAction(saved.getId(), "PAYMENT_COMPLETED",
                    "invoiceId=" + saved.getInvoiceId() + ", externalRef=" + saved.getExternalRef());
            log.info("Payment completed: {}", id);
            return toResponse(saved);
        }

        attempt.setResponse("{\"status\":\"FAILED\",\"reason\":\"" + FAILURE_REASON + "\",\"attemptNo\":" + attemptNo + "}");
        paymentAttemptRepository.save(attempt);

        if (payment.getInvoiceId() != null) {
            scheduleRetryOrFail(payment);
        } else {
            payment.setStatus("FAILED");
            writePaymentFailedToOutbox(payment, DEFAULT_CURRENCY);
        }
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

        if (WALLET_METHOD.equals(saved.getMethod()) && saved.getCustomerId() != null) {
            walletService.credit(saved.getCustomerId(), saved.getAmount());
        }

        PaymentRefundedEvent event = new PaymentRefundedEvent(
                UUID.randomUUID(),
                PAYMENT_REFUNDED_EVENT,
                saved.getId(),
                saved.getInvoiceId(),
                saved.getCustomerId(),
                saved.getAmount(),
                DEFAULT_CURRENCY,
                LocalDateTime.now()
        );
        UUID aggregateId = saved.getInvoiceId() != null ? saved.getInvoiceId() : saved.getId();
        outboxEventWriter.write(aggregateId, PAYMENT_AGGREGATE_TYPE, PAYMENT_REFUNDED_EVENT, event);

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

    @Override
    @Transactional
    public void handleInvoiceGenerated(InvoiceGeneratedEvent event) {
        if (event.eventId() != null && processedEventRepository.existsById(event.eventId())) {
            log.warn("InvoiceGenerated event {} already processed, skipping", event.eventId());
            return;
        }

        if (paymentRepository.findByInvoiceId(event.invoiceId()).stream()
                .anyMatch(p -> !"FAILED".equals(p.getStatus()) && !"REFUNDED".equals(p.getStatus()))) {
            log.warn("Active payment already exists for invoiceId: {}", event.invoiceId());
        } else {
            Payment payment = new Payment();
            payment.setInvoiceId(event.invoiceId());
            payment.setCustomerId(event.customerId());
            payment.setAmount(event.grandTotal());
            payment.setMethod("CREDIT_CARD");
            payment.setStatus("PENDING");
            Payment saved = paymentRepository.save(payment);
            auditLogService.logPaymentAction(saved.getId(), "PAYMENT_CREATED",
                    "autoPay invoiceId=" + event.invoiceId() + ", amount=" + event.grandTotal());
            processPayment(saved.getId());
        }

        if (event.eventId() != null) {
            processedEventRepository.save(new ProcessedEvent(event.eventId(), INVOICE_GENERATED_EVENT));
        }
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
                    "orderId=" + payment.getOrderId() + ", tariffCode=" + tariffCode);
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

    private boolean chargePayment(Payment payment) {
        if (WALLET_METHOD.equals(payment.getMethod())) {
            return walletService.charge(payment.getCustomerId(), payment.getAmount());
        }
        return paymentGateway.charge(payment.getMethod(), payment.getAmount());
    }

    private void scheduleRetryOrFail(Payment payment) {
        if (payment.getFirstFailedAt() == null) {
            payment.setFirstFailedAt(LocalDateTime.now());
        }
        int retryCount = payment.getRetryCount() + 1;
        payment.setRetryCount(retryCount);

        List<Integer> delays = paymentRetryProperties.getDelayHours();
        if (retryCount <= delays.size()) {
            payment.setStatus("RETRY_PENDING");
            int delay = delays.get(retryCount - 1);
            LocalDateTime nextRetry = paymentRetryProperties.isUseMinutesForDev()
                    ? payment.getFirstFailedAt().plusMinutes(delay)
                    : payment.getFirstFailedAt().plusHours(delay);
            payment.setNextRetryAt(nextRetry);
            log.info("Payment {} scheduled for retry {} at {}", payment.getId(), retryCount, nextRetry);
        } else {
            payment.setStatus("FAILED");
            payment.setNextRetryAt(null);
            writePaymentFailedToOutbox(payment, DEFAULT_CURRENCY);
            auditLogService.logPaymentAction(payment.getId(), "PAYMENT_FAILED",
                    "invoiceId=" + payment.getInvoiceId() + ", reason=" + FAILURE_REASON);
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
        UUID aggregateId = payment.getOrderId() != null ? payment.getOrderId()
                : (payment.getInvoiceId() != null ? payment.getInvoiceId() : payment.getId());
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
        UUID aggregateId = payment.getOrderId() != null ? payment.getOrderId()
                : (payment.getInvoiceId() != null ? payment.getInvoiceId() : payment.getId());
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
