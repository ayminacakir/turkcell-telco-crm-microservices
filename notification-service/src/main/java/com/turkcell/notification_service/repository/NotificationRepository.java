package com.turkcell.notification_service.repository;

import com.turkcell.notification_service.domain.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserId(UUID userId);

    org.springframework.data.domain.Page<Notification> findByUserId(UUID userId, org.springframework.data.domain.Pageable pageable);
}
