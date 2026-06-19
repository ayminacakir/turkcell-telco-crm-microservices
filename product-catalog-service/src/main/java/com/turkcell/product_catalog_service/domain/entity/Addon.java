package com.turkcell.product_catalog_service.domain.entity;

import com.turkcell.product_catalog_service.domain.enums.AddonType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "addons", schema = "product_catalog_service")
public class Addon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AddonType type;

    @Column(name = "validity_days")
    private Integer validityDays;

    @OneToMany(mappedBy = "addon", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TariffAddon> tariffAddons = new ArrayList<>();

    public Addon() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public AddonType getType() { return type; }
    public void setType(AddonType type) { this.type = type; }

    public Integer getValidityDays() { return validityDays; }
    public void setValidityDays(Integer validityDays) { this.validityDays = validityDays; }

    public List<TariffAddon> getTariffAddons() { return tariffAddons; }
    public void setTariffAddons(List<TariffAddon> tariffAddons) { this.tariffAddons = tariffAddons; }
}
