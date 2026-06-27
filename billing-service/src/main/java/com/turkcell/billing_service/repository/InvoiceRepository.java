package com.turkcell.billing_service.repository;

import com.turkcell.billing_service.domain.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByCustomerId(UUID customerId);
    List<Invoice> findBySubscriptionId(UUID subscriptionId);
    List<Invoice> findByStatus(String status);
}