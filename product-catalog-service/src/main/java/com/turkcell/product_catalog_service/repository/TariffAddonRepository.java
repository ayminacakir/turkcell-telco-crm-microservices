package com.turkcell.product_catalog_service.repository;

import com.turkcell.product_catalog_service.domain.entity.TariffAddon;
import com.turkcell.product_catalog_service.domain.entity.TariffAddonId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TariffAddonRepository extends JpaRepository<TariffAddon, TariffAddonId> {

    List<TariffAddon> findByTariff_Code(String tariffCode);
}
