package com.turkcell.payment_service.repository;

import com.turkcell.payment_service.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByInvoiceId(UUID invoiceId);
    Optional<Payment> findByInvoiceIdAndStatusNot(UUID invoiceId, String status);
    List<Payment> findByStatus(String status);
}