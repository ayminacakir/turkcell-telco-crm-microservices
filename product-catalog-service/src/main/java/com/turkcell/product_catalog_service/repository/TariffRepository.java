package com.turkcell.product_catalog_service.repository;

import com.turkcell.product_catalog_service.domain.entity.Tariff;
import com.turkcell.product_catalog_service.domain.enums.TariffStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TariffRepository extends JpaRepository<Tariff, UUID> {

    Optional<Tariff> findByCode(String code);

    boolean existsByCode(String code);

    List<Tariff> findByStatus(TariffStatus status);

    /**
     * Verilen tarihte gercekten gecerli tarifeler: status eslesir VE
     * effectiveFrom/effectiveTo araligi bugunu kapsar (null = sinirsiz).
     * Filtre DB'de yapilir ki pagination toplam sayilari dogru olsun.
     */
    @Query("""
            SELECT t FROM Tariff t
            WHERE t.status = :status
              AND (t.effectiveFrom IS NULL OR t.effectiveFrom <= :today)
              AND (t.effectiveTo IS NULL OR t.effectiveTo >= :today)
            """)
    Page<Tariff> findValidOn(@Param("status") TariffStatus status,
                             @Param("today") LocalDate today,
                             Pageable pageable);
}
