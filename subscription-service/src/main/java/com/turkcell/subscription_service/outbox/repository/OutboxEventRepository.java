package com.turkcell.subscription_service.outbox.repository;

import com.turkcell.subscription_service.outbox.entity.OutboxEvent;
import com.turkcell.subscription_service.outbox.enums.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatus(OutboxStatus status);
}
