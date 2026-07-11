package com.turkcell.payment_service.outbox.repository;

import com.turkcell.payment_service.outbox.entity.OutboxEvent;
import com.turkcell.payment_service.outbox.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatus(OutboxStatus status);
}
