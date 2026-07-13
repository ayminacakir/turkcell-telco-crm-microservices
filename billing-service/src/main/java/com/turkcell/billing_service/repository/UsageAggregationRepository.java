package com.turkcell.billing_service.repository;

import com.turkcell.billing_service.domain.entity.UsageAggregation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsageAggregationRepository extends JpaRepository<UsageAggregation, UUID> {
    Optional<UsageAggregation> findBySubscriptionIdAndPeriodStartAndPeriodEnd(
            UUID subscriptionId, LocalDate periodStart, LocalDate periodEnd);
}
