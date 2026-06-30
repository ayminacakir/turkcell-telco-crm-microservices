package com.turkcell.usage_service.repository;

import com.turkcell.usage_service.domain.entity.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {
    List<UsageRecord> findBySubscriptionId(UUID subscriptionId);
    List<UsageRecord> findBySubscriptionIdAndRecordedAtBetween(
        UUID subscriptionId, LocalDateTime from, LocalDateTime to);
    List<UsageRecord> findBySubscriptionIdAndType(
        UUID subscriptionId, UsageRecord.UsageType type);
}