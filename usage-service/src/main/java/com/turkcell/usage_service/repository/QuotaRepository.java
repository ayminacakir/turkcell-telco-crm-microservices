package com.turkcell.usage_service.repository;

import com.turkcell.usage_service.domain.entity.Quota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuotaRepository extends JpaRepository<Quota, UUID> {
    List<Quota> findBySubscriptionId(UUID subscriptionId);
    Optional<Quota> findBySubscriptionIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
    UUID subscriptionId, LocalDate date1, LocalDate date2);
    boolean existsBySubscriptionId(UUID subscriptionId);
    Optional<Quota> findFirstBySubscriptionIdOrderByPeriodStartDesc(UUID subscriptionId);
}