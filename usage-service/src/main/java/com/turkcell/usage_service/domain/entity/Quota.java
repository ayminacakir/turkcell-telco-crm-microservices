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

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "minutes_total", nullable = false)
    private Integer minutesTotal;

    @Column(name = "sms_total", nullable = false)
    private Integer smsTotal;

    @Column(name = "mb_total", nullable = false)
    private Integer mbTotal;

    @Column(name = "minutes_remaining", nullable = false)
    private Integer minutesRemaining;

    @Column(name = "sms_remaining", nullable = false)
    private Integer smsRemaining;

    @Column(name = "mb_remaining", nullable = false)
    private Integer mbRemaining;

    @Column(name = "voice_used", nullable = false)
    private Integer voiceUsed = 0;

    @Column(name = "sms_used", nullable = false)
    private Integer smsUsed = 0;

    @Column(name = "data_mb_used", nullable = false)
    private Integer dataMbUsed = 0;

    @Column(name = "overage_voice_minutes", nullable = false)
    private Integer overageVoiceMinutes = 0;

    @Column(name = "overage_sms", nullable = false)
    private Integer overageSms = 0;

    @Column(name = "overage_data_mb", nullable = false)
    private Integer overageDataMb = 0;

    public Quota() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public Integer getMinutesTotal() { return minutesTotal; }
    public void setMinutesTotal(Integer minutesTotal) { this.minutesTotal = minutesTotal; }

    public Integer getSmsTotal() { return smsTotal; }
    public void setSmsTotal(Integer smsTotal) { this.smsTotal = smsTotal; }

    public Integer getMbTotal() { return mbTotal; }
    public void setMbTotal(Integer mbTotal) { this.mbTotal = mbTotal; }

    public Integer getMinutesRemaining() { return minutesRemaining; }
    public void setMinutesRemaining(Integer minutesRemaining) { this.minutesRemaining = minutesRemaining; }

    public Integer getSmsRemaining() { return smsRemaining; }
    public void setSmsRemaining(Integer smsRemaining) { this.smsRemaining = smsRemaining; }

    public Integer getMbRemaining() { return mbRemaining; }
    public void setMbRemaining(Integer mbRemaining) { this.mbRemaining = mbRemaining; }

    public Integer getVoiceUsed() { return voiceUsed; }
    public void setVoiceUsed(Integer voiceUsed) { this.voiceUsed = voiceUsed; }

    public Integer getSmsUsed() { return smsUsed; }
    public void setSmsUsed(Integer smsUsed) { this.smsUsed = smsUsed; }

    public Integer getDataMbUsed() { return dataMbUsed; }
    public void setDataMbUsed(Integer dataMbUsed) { this.dataMbUsed = dataMbUsed; }

    public Integer getOverageVoiceMinutes() { return overageVoiceMinutes; }
    public void setOverageVoiceMinutes(Integer overageVoiceMinutes) { this.overageVoiceMinutes = overageVoiceMinutes; }

    public Integer getOverageSms() { return overageSms; }
    public void setOverageSms(Integer overageSms) { this.overageSms = overageSms; }

    public Integer getOverageDataMb() { return overageDataMb; }
    public void setOverageDataMb(Integer overageDataMb) { this.overageDataMb = overageDataMb; }
}
