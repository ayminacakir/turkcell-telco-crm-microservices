package com.turkcell.payment_service.repository;

import com.turkcell.payment_service.domain.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    List<PaymentAttempt> findByPaymentIdOrderByAttemptNoAsc(UUID paymentId);
    int countByPaymentId(UUID paymentId);
}