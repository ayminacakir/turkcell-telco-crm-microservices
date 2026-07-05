package com.turkcell.product_catalog_service.repository;

import com.turkcell.product_catalog_service.domain.entity.Addon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AddonRepository extends JpaRepository<Addon, UUID> {

    Optional<Addon> findByCode(String code);

    boolean existsByCode(String code);
}
