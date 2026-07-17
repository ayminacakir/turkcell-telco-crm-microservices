package com.turkcell.product_catalog_service.repository;

import com.turkcell.product_catalog_service.domain.entity.TariffVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TariffVersionRepository extends JpaRepository<TariffVersion, UUID> {

    List<TariffVersion> findByCodeOrderByVersionDesc(String code);
}
