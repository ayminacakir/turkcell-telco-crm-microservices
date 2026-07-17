package com.turkcell.order_service.outbox.repository;

import com.turkcell.order_service.outbox.entity.OutboxEvent;
import com.turkcell.order_service.outbox.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatus(OutboxStatus status);

    List<OutboxEvent> findByStatusInAndRetryCountLessThan(List<OutboxStatus> statuses, int maxRetryCount);
}
