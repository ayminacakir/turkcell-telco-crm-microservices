package com.turkcell.subscription_service.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import com.turkcell.subscription_service.domain.enums.MsisdnStatus;

@Entity
@Table(name = "msisdn_pool")
public class MsisdnPool {

    @Id
    @Column(nullable = false, unique = true)
    private String msisdn;// msisdn telefon numarası benzersiz olmalı

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MsisdnStatus status;

    private LocalDateTime reservedUntil;

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public MsisdnStatus getStatus() {
        return status;
    }

    public void setStatus(MsisdnStatus status) {
        this.status = status;
    }

    public LocalDateTime getReservedUntil() {
        return reservedUntil;
    }

    public void setReservedUntil(LocalDateTime reservedUntil) {
        this.reservedUntil = reservedUntil;
    }

}