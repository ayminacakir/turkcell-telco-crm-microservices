package com.turkcell.subscription_service.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import com.turkcell.subscription_service.domain.enums.SubscriptionStatus;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;// customerId customer-service tarafındaki müşteriyi temsil eder. Mikroservis
                            // mimarisinde farklı servisin tablosuna FK verilmez.

    @OneToOne
    @JoinColumn(name = "msisdn", referencedColumnName = "msisdn", nullable = false)
    private MsisdnPool msisdnPool;

    @Column(nullable = false)
    private String tariffCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    private LocalDateTime activatedAt;

    private LocalDateTime terminatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public MsisdnPool getMsisdnPool() {
        return msisdnPool;
    }

    public void setMsisdnPool(MsisdnPool msisdnPool) {
        this.msisdnPool = msisdnPool;
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