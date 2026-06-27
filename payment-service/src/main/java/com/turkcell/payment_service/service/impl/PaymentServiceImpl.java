package com.turkcell.payment_service.service.impl;

import com.turkcell.payment_service.domain.entity.Payment;
import com.turkcell.payment_service.domain.entity.PaymentAttempt;
import com.turkcell.payment_service.dto.request.CreatePaymentRequest;
import com.turkcell.payment_service.dto.response.PaymentAttemptResponse;
import com.turkcell.payment_service.dto.response.PaymentResponse;
import com.turkcell.payment_service.event.PaymentCompletedEvent;
import com.turkcell.payment_service.kafka.KafkaProducerService;
import com.turkcell.payment_service.repository.PaymentAttemptRepository;
import com.turkcell.payment_service.repository.PaymentRepository;
import com.turkcell.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
        // FR-26: İdempotency — aynı invoice için aktif ödeme var mı?
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

        // Mock PSP — gerçekte banka API'si çağrılır
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
    
    // Kafka event publish et
    kafkaProducerService.publishPaymentCompleted(new PaymentCompletedEvent(
        saved.getId(),
        saved.getInvoiceId(),
        saved.getAmount(),
        saved.getMethod(),
        "COMPLETED",
        saved.getExternalRef(),
        saved.getPaidAt()
    ));
    
    log.info("Payment completed: {}", id);
    return toResponse(saved);
} else {
    if (attemptNo >= 3) {
        payment.setStatus("FAILED");
        paymentAttemptRepository.save(attempt);
        Payment saved = paymentRepository.save(payment);
        
        // Kafka failed event publish et
        kafkaProducerService.publishPaymentFailed(new PaymentCompletedEvent(
            saved.getId(),
            saved.getInvoiceId(),
            saved.getAmount(),
            saved.getMethod(),
            "FAILED",
            null,
            null
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

    // Mock PSP — %80 başarı oranı
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