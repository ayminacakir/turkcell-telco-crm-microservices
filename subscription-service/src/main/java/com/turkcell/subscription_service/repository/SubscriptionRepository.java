package com.turkcell.subscription_service.repository;

import com.turkcell.subscription_service.entity.Subscription;
import com.turkcell.subscription_service.enums.SubscriptionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByCustomerId(UUID customerId);

    List<Subscription> findByStatus(SubscriptionStatus status);

    Optional<Subscription> findByOrderId(UUID orderId);
}
