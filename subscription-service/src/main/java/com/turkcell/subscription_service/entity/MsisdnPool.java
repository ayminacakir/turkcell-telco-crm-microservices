package com.turkcell.subscription_service.entity;

import com.turkcell.subscription_service.enums.MsisdnStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "msisdn_pool")
public class MsisdnPool {

    @Id
    @Column(nullable = false, unique = true)
    private String msisdn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MsisdnStatus status;

    private LocalDateTime reservedUntil;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = MsisdnStatus.FREE;
        }
    }

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
