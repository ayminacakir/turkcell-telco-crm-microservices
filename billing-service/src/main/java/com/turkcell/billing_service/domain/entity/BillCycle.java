package com.turkcell.billing_service.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bill_cycles", schema = "billing_service")
public class BillCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    // subscription.activated event'inden gelir; manuel olusturulan cycle'larda bos olabilir.
    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "tariff_code", length = 50)
    private String tariffCode;

    @Column(name = "monthly_fee", precision = 12, scale = 2)
    private java.math.BigDecimal monthlyFee;

    @Column(name = "day_of_month", nullable = false)
    private Integer dayOfMonth;

    @Column(name = "next_run_date", nullable = false)
    private LocalDate nextRunDate;

    public BillCycle() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }

    public String getTariffCode() { return tariffCode; }
    public void setTariffCode(String tariffCode) { this.tariffCode = tariffCode; }

    public java.math.BigDecimal getMonthlyFee() { return monthlyFee; }
    public void setMonthlyFee(java.math.BigDecimal monthlyFee) { this.monthlyFee = monthlyFee; }

    public Integer getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Integer dayOfMonth) { this.dayOfMonth = dayOfMonth; }

    public LocalDate getNextRunDate() { return nextRunDate; }
    public void setNextRunDate(LocalDate nextRunDate) { this.nextRunDate = nextRunDate; }
}
