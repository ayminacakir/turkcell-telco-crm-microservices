package com.turkcell.product_catalog_service.service;

import com.turkcell.product_catalog_service.dto.AddonCreateRequest;
import com.turkcell.product_catalog_service.dto.AddonResponse;

import java.util.List;

public interface AddonService {

    AddonResponse create(AddonCreateRequest request);

    AddonResponse getByCode(String code);

    List<AddonResponse> getByTariffCode(String tariffCode);

    /**
     * Bir ek paketi bir tarifeye bağlar (TariffAddon many-to-many ilişkisi).
     * Idempotent: ilişki zaten varsa hata fırlatmaz, sessizce no-op.
     */
    void linkToTariff(String tariffCode, String addonCode);

    /**
     * Bir tarife ile ek paket arasındaki bağı kaldırır.
     */
    void unlinkFromTariff(String tariffCode, String addonCode);
}
