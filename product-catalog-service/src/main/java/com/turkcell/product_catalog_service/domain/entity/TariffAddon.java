package com.turkcell.product_catalog_service.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tariff_addons", schema = "product_catalog_service")
@IdClass(TariffAddonId.class)
public class TariffAddon {

    @Id
    @ManyToOne
    @JoinColumn(name = "tariff_id", nullable = false)
    private Tariff tariff;

    @Id
    @ManyToOne
    @JoinColumn(name = "addon_id", nullable = false)
    private Addon addon;

    public TariffAddon() {}

    public Tariff getTariff() { return tariff; }
    public void setTariff(Tariff tariff) { this.tariff = tariff; }

    public Addon getAddon() { return addon; }
    public void setAddon(Addon addon) { this.addon = addon; }
}
