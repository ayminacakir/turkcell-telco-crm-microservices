package com.turkcell.product_catalog_service.domain.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TariffAddonId implements Serializable {

    private UUID tariff;
    private UUID addon;

    public TariffAddonId() {}

    public TariffAddonId(UUID tariff, UUID addon) {
        this.tariff = tariff;
        this.addon = addon;
    }

    public UUID getTariff() { return tariff; }
    public void setTariff(UUID tariff) { this.tariff = tariff; }

    public UUID getAddon() { return addon; }
    public void setAddon(UUID addon) { this.addon = addon; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TariffAddonId that)) return false;
        return Objects.equals(tariff, that.tariff) && Objects.equals(addon, that.addon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tariff, addon);
    }
}
