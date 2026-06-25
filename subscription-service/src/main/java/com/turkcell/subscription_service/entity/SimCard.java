package com.turkcell.subscription_service.entity;

import com.turkcell.subscription_service.enums.SimCardStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "sim_cards")
public class SimCard {

    @Id
    @Column(nullable = false, unique = true)
    private String iccid;

    @Column(nullable = false, unique = true)
    private String imsi;

    @Column(nullable = false)
    private String msisdn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SimCardStatus status;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = SimCardStatus.FREE;
        }
    }

    public String getIccid() {
        return iccid;
    }

    public void setIccid(String iccid) {
        this.iccid = iccid;
    }

    public String getImsi() {
        return imsi;
    }

    public void setImsi(String imsi) {
        this.imsi = imsi;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public SimCardStatus getStatus() {
        return status;
    }

    public void setStatus(SimCardStatus status) {
        this.status = status;
    }
}
