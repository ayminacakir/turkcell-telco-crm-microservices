package com.turkcell.product_catalog_service.service;

import com.turkcell.product_catalog_service.dto.PageResponse;
import com.turkcell.product_catalog_service.dto.TariffCreateRequest;
import com.turkcell.product_catalog_service.dto.TariffResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TariffService {

    TariffResponse create(TariffCreateRequest request);

    TariffResponse getByCode(String code);

    PageResponse<TariffResponse> getAll(Pageable pageable);

    PageResponse<TariffResponse> getActive(Pageable pageable);

    TariffResponse updatePrice(String code, java.math.BigDecimal newMonthlyFee);

    /** FR-08: Bir tarifenin arsivlenmis eski versiyonlarini (en yeniden eskiye) doner. */
    List<TariffResponse> getVersions(String code);
}
