package com.turkcell.product_catalog_service.service;

import com.turkcell.product_catalog_service.dto.TariffCreateRequest;
import com.turkcell.product_catalog_service.dto.TariffResponse;

import java.util.List;

public interface TariffService {

    TariffResponse create(TariffCreateRequest request);

    TariffResponse getByCode(String code);

    List<TariffResponse> getAll();

    List<TariffResponse> getActive();
}
