package com.turkcell.usage_service.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "quotas", schema = "usage_service")
public class Quota {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "minutes_remaining", nullable = false)
    private Integer minutesRemaining;

    @Column(name = "sms_remaining", nullable = false)
    private Integer smsRemaining;

    @Column(name = "mb_remaining", nullable = false)
    private Integer mbRemaining;

    public Quota() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public Integer getMinutesRemaining() { return minutesRemaining; }
    public void setMinutesRemaining(Integer minutesRemaining) { this.minutesRemaining = minutesRemaining; }

    public Integer getSmsRemaining() { return smsRemaining; }
    public void setSmsRemaining(Integer smsRemaining) { this.smsRemaining = smsRemaining; }

    public Integer getMbRemaining() { return mbRemaining; }
    public void setMbRemaining(Integer mbRemaining) { this.mbRemaining = mbRemaining; }
}
