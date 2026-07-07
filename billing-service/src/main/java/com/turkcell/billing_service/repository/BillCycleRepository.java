package com.turkcell.billing_service.repository;

import com.turkcell.billing_service.domain.entity.BillCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillCycleRepository extends JpaRepository<BillCycle, UUID> {
    List<BillCycle> findByCustomerId(UUID customerId);
    Optional<BillCycle> findByCustomerIdAndDayOfMonth(UUID customerId, int dayOfMonth);
    boolean existsByCustomerId(UUID customerId);
    
}