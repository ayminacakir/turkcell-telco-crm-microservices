package com.turkcell.subscription_service.entity;

import com.turkcell.subscription_service.enums.MnpStatus;
import com.turkcell.subscription_service.enums.SubscriptionStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false, unique = true)
    private String msisdn;

    @Column(nullable = false)
    private String tariffCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MnpStatus mnpStatus;

    @Column(nullable = false)
    private LocalDateTime activatedAt;

    private LocalDateTime terminatedAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = SubscriptionStatus.ACTIVE;
        }

        if (this.mnpStatus == null) {
            this.mnpStatus = MnpStatus.NOT_REQUESTED;
        }

        if (this.activatedAt == null) {
            this.activatedAt = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public String getTariffCode() {
        return tariffCode;
    }

    public void setTariffCode(String tariffCode) {
        this.tariffCode = tariffCode;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public MnpStatus getMnpStatus() {
        return mnpStatus;
    }

    public void setMnpStatus(MnpStatus mnpStatus) {
        this.mnpStatus = mnpStatus;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public LocalDateTime getTerminatedAt() {
        return terminatedAt;
    }

    public void setTerminatedAt(LocalDateTime terminatedAt) {
        this.terminatedAt = terminatedAt;
    }
}
