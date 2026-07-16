package com.turkcell.product_catalog_service.domain.entity;

import com.turkcell.product_catalog_service.domain.enums.TariffStatus;
import com.turkcell.product_catalog_service.domain.enums.TariffType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FR-08: Tarife degisikliklerinde tarifenin ONCEKI halinin snapshot'i.
 * Eski abonelerin bagli oldugu tarife kosullari bu tablo uzerinden korunur;
 * tariffs tablosundaki satir her zaman guncel (son) versiyonu temsil eder.
 */
@Entity
@Table(name = "tariff_versions", schema = "product_catalog_service",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tariff_id", "version"}))
public class TariffVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tariff_id", nullable = false)
    private UUID tariffId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TariffType type;

    @Column(name = "monthly_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyFee;

    @Column(name = "minutes_included")
    private Integer minutesIncluded;

    @Column(name = "sms_included")
    private Integer smsIncluded;

    @Column(name = "data_mb_included")
    private Integer dataMbIncluded;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TariffStatus status;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "target_segment", length = 50)
    private String targetSegment;

    @Column(name = "archived_at", nullable = false)
    private LocalDateTime archivedAt;

    public TariffVersion() {}

    /** Verilen tarifenin su anki halinden bir snapshot olusturur. */
    public static TariffVersion snapshotOf(Tariff tariff) {
        TariffVersion v = new TariffVersion();
        v.tariffId = tariff.getId();
        v.version = tariff.getVersion();
        v.code = tariff.getCode();
        v.name = tariff.getName();
        v.type = tariff.getType();
        v.monthlyFee = tariff.getMonthlyFee();
        v.minutesIncluded = tariff.getMinutesIncluded();
        v.smsIncluded = tariff.getSmsIncluded();
        v.dataMbIncluded = tariff.getDataMbIncluded();
        v.status = tariff.getStatus();
        v.effectiveFrom = tariff.getEffectiveFrom();
        v.effectiveTo = tariff.getEffectiveTo();
        v.targetSegment = tariff.getTargetSegment();
        v.archivedAt = LocalDateTime.now();
        return v;
    }

    public UUID getId() { return id; }

    public UUID getTariffId() { return tariffId; }
    public void setTariffId(UUID tariffId) { this.tariffId = tariffId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public TariffType getType() { return type; }
    public void setType(TariffType type) { this.type = type; }

    public BigDecimal getMonthlyFee() { return monthlyFee; }
    public void setMonthlyFee(BigDecimal monthlyFee) { this.monthlyFee = monthlyFee; }

    public Integer getMinutesIncluded() { return minutesIncluded; }
    public void setMinutesIncluded(Integer minutesIncluded) { this.minutesIncluded = minutesIncluded; }

    public Integer getSmsIncluded() { return smsIncluded; }
    public void setSmsIncluded(Integer smsIncluded) { this.smsIncluded = smsIncluded; }

    public Integer getDataMbIncluded() { return dataMbIncluded; }
    public void setDataMbIncluded(Integer dataMbIncluded) { this.dataMbIncluded = dataMbIncluded; }

    public TariffStatus getStatus() { return status; }
    public void setStatus(TariffStatus status) { this.status = status; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public String getTargetSegment() { return targetSegment; }
    public void setTargetSegment(String targetSegment) { this.targetSegment = targetSegment; }

    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
}
