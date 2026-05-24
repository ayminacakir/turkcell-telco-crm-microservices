package com.turkcell.product_catalog_service.domain.entity;

import com.turkcell.product_catalog_service.domain.enums.TariffStatus;
import com.turkcell.product_catalog_service.domain.enums.TariffType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tariffs", schema = "product_catalog_service")
public class Tariff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
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

    @OneToMany(mappedBy = "tariff", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TariffAddon> tariffAddons = new ArrayList<>();

    public Tariff() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

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

    public List<TariffAddon> getTariffAddons() { return tariffAddons; }
    public void setTariffAddons(List<TariffAddon> tariffAddons) { this.tariffAddons = tariffAddons; }
}
