package com.turkcell.subscription_service.domain.entity;

import com.turkcell.subscription_service.domain.enums.SimCardStatus;

import jakarta.persistence.*;

@Entity
@Table(name = "sim_cards")
public class SimCard {

    @Id
    @Column(nullable = false, unique = true)
    private String iccid;// ICCID SIM kartın seri numarasıdır.

    @Column(nullable = false, unique = true)
    private String imsi;// IMSI SIM kartın uluslararası mobil abone kimliği numarasıdır.

    @OneToOne
    @JoinColumn(name = "msisdn", referencedColumnName = "msisdn")
    private MsisdnPool msisdnPool;// MSISDN SIM kartın telefon numarasıdır. Her SIM kartın benzersiz bir MSISDN'si
    // vardır.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SimCardStatus status;

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

    public MsisdnPool getMsisdnPool() {
        return msisdnPool;
    }

    public void setMsisdnPool(MsisdnPool msisdnPool) {
        this.msisdnPool = msisdnPool;
    }

    public SimCardStatus getStatus() {
        return status;
    }

    public void setStatus(SimCardStatus status) {
        this.status = status;
    }

}