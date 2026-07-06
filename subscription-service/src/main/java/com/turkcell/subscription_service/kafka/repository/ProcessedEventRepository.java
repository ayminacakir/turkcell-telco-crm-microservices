package com.turkcell.subscription_service.kafka.repository;

import com.turkcell.subscription_service.kafka.entity.ProcessedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
