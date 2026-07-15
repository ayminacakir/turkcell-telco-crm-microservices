package com.turkcell.notification_service.repository;

import com.turkcell.notification_service.domain.entity.NotificationPreference;
import com.turkcell.notification_service.domain.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    Optional<NotificationPreference> findByUserIdAndChannel(UUID userId, NotificationChannel channel);
    List<NotificationPreference> findByUserId(UUID userId);
}
