package com.turkcell.payment_service.service;

import com.turkcell.payment_service.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRetryScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 60000)
    public void retryDuePayments() {
        List<com.turkcell.payment_service.domain.entity.Payment> duePayments =
                paymentRepository.findByStatusAndNextRetryAtLessThanEqual("RETRY_PENDING", LocalDateTime.now());

        for (var payment : duePayments) {
            log.info("Retrying payment {} (attempt {})", payment.getId(), payment.getRetryCount());
            paymentService.processPayment(payment.getId());
        }
    }
}
