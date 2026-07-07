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
import com.turkcell.payment_service.kafka.KafkaProducerService;
import com.turkcell.payment_service.repository.PaymentAttemptRepository;
import com.turkcell.payment_service.repository.PaymentRepository;
import com.turkcell.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final KafkaProducerService kafkaProducerService;

    @Override
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

        return toResponse(paymentRepository.save(payment));
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
    public PaymentResponse processPayment(UUID id) {
        Payment payment = findPaymentById(id);

        if ("COMPLETED".equals(payment.getStatus())) {
            throw new RuntimeException("Payment already completed: " + id);
        }

        int attemptNo = paymentAttemptRepository.countByPaymentId(id) + 1;
        boolean success = mockPaymentGateway(payment.getMethod());

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(payment);
        attempt.setAttemptNo(attemptNo);
        attempt.setAttemptedAt(LocalDateTime.now());

        if (success) {
            payment.setStatus("COMPLETED");
            payment.setPaidAt(LocalDateTime.now());
            payment.setExternalRef("PSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            attempt.setResponse("{\"status\":\"SUCCESS\",\"externalRef\":\"" + payment.getExternalRef() + "\"}");

            paymentAttemptRepository.save(attempt);
            Payment saved = paymentRepository.save(payment);

            kafkaProducerService.publishPaymentCompleted(new PaymentCompletedEvent(
                    UUID.randomUUID(),
                    "PaymentCompleted",
                    saved.getId(),
                    saved.getOrderId(),
                    saved.getCustomerId(),
                    saved.getAmount(),
                    "TRY",
                    "COMPLETED",
                    saved.getPaidAt(),
                    null,
                    null,
                    null,
                    null
            ));

            log.info("Payment completed: {}", id);
            return toResponse(saved);
        } else {
            if (attemptNo >= 3) {
                payment.setStatus("FAILED");
                paymentAttemptRepository.save(attempt);
                Payment saved = paymentRepository.save(payment);

                kafkaProducerService.publishPaymentFailed(new PaymentFailedEvent(
                        UUID.randomUUID(),
                        "PaymentFailed",
                        saved.getId(),
                        saved.getOrderId(),
                        saved.getCustomerId(),
                        saved.getAmount(),
                        "TRY",
                        "FAILED",
                        "Insufficient funds",
                        LocalDateTime.now()
                ));
                return toResponse(saved);
            }
            attempt.setResponse("{\"status\":\"FAILED\",\"reason\":\"Insufficient funds\",\"attemptNo\":" + attemptNo + "}");
            paymentAttemptRepository.save(attempt);
            return toResponse(paymentRepository.save(payment));
        }
    }

    @Override
    public PaymentResponse refundPayment(UUID id) {
        Payment payment = findPaymentById(id);

        if (!"COMPLETED".equals(payment.getStatus())) {
            throw new RuntimeException("Only completed payments can be refunded");
        }

        payment.setStatus("REFUNDED");
        log.info("Payment refunded: {}", id);
        return toResponse(paymentRepository.save(payment));
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
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (paymentRepository.findByOrderId(event.orderId()).isPresent()) {
            log.warn("Payment already exists for orderId: {}, skipping", event.orderId());
            return;
        }

        Payment payment = new Payment();
        payment.setOrderId(event.orderId());
        payment.setCustomerId(event.customerId());
        payment.setAmount(event.totalAmount());
        payment.setMethod("CREDIT_CARD");
        payment.setStatus("PENDING");
        Payment saved = paymentRepository.save(payment);

        processOrderPayment(saved.getId(), event);
    }

    private void processOrderPayment(UUID paymentId, OrderCreatedEvent orderEvent) {
        Payment payment = findPaymentById(paymentId);
        boolean success = mockPaymentGateway(payment.getMethod());

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(payment);
        attempt.setAttemptNo(1);
        attempt.setAttemptedAt(LocalDateTime.now());

        OrderCreatedItemEvent tariffItem = orderEvent.items().stream()
                .filter(item -> "TARIFF".equals(item.productType()))
                .findFirst()
                .orElse(null);

        String tariffCode = tariffItem != null ? tariffItem.productCode() : null;
        Integer minutesIncluded = tariffItem != null ? tariffItem.minutesIncluded() : null;
        Integer smsIncluded = tariffItem != null ? tariffItem.smsIncluded() : null;
        Integer dataMbIncluded = tariffItem != null ? tariffItem.dataMbIncluded() : null;

        if (success) {
            payment.setStatus("COMPLETED");
            payment.setPaidAt(LocalDateTime.now());
            payment.setExternalRef("PSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            attempt.setResponse("{\"status\":\"SUCCESS\",\"externalRef\":\"" + payment.getExternalRef() + "\"}");
            paymentAttemptRepository.save(attempt);
            paymentRepository.save(payment);

            kafkaProducerService.publishPaymentCompleted(new PaymentCompletedEvent(
                    UUID.randomUUID(),
                    "PaymentCompleted",
                    payment.getId(),
                    orderEvent.orderId(),
                    orderEvent.customerId(),
                    payment.getAmount(),
                    orderEvent.currency(),
                    "COMPLETED",
                    payment.getPaidAt(),
                    tariffCode,
                    minutesIncluded,
                    smsIncluded,
                    dataMbIncluded
            ));
            log.info("Order payment completed: orderId={}, tariffCode={}", orderEvent.orderId(), tariffCode);
        } else {
            payment.setStatus("FAILED");
            attempt.setResponse("{\"status\":\"FAILED\",\"reason\":\"Insufficient funds\"}");
            paymentAttemptRepository.save(attempt);
            paymentRepository.save(payment);

            kafkaProducerService.publishPaymentFailed(new PaymentFailedEvent(
                    UUID.randomUUID(),
                    "PaymentFailed",
                    payment.getId(),
                    orderEvent.orderId(),
                    orderEvent.customerId(),
                    payment.getAmount(),
                    orderEvent.currency(),
                    "FAILED",
                    "Insufficient funds",
                    LocalDateTime.now()
            ));
            log.info("Order payment failed: orderId={}", orderEvent.orderId());
        }
    }

    private boolean mockPaymentGateway(String method) {
        return Math.random() > 0.2;
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