package com.turkcell.product_catalog_service.service.impl;

import com.turkcell.product_catalog_service.domain.entity.Addon;
import com.turkcell.product_catalog_service.domain.entity.TariffAddon;
import com.turkcell.product_catalog_service.dto.AddonCreateRequest;
import com.turkcell.product_catalog_service.dto.AddonResponse;
import com.turkcell.product_catalog_service.exception.DuplicateCodeException;
import com.turkcell.product_catalog_service.exception.ResourceNotFoundException;
import com.turkcell.product_catalog_service.repository.AddonRepository;
import com.turkcell.product_catalog_service.repository.TariffAddonRepository;
import com.turkcell.product_catalog_service.service.AddonService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AddonServiceImpl implements AddonService {

    private static final String CURRENCY = "TRY";

    private final AddonRepository addonRepository;
    private final TariffAddonRepository tariffAddonRepository;

    public AddonServiceImpl(AddonRepository addonRepository, TariffAddonRepository tariffAddonRepository) {
        this.addonRepository = addonRepository;
        this.tariffAddonRepository = tariffAddonRepository;
    }

    @Override
    public AddonResponse create(AddonCreateRequest request) {
        if (addonRepository.existsByCode(request.code())) {
            throw new DuplicateCodeException("Addon with code " + request.code() + " already exists");
        }

        Addon addon = new Addon();
        addon.setCode(request.code());
        addon.setName(request.name());
        addon.setPrice(request.price());
        addon.setType(request.type());
        addon.setValidityDays(request.validityDays());

        return toResponse(addonRepository.save(addon));
    }

    @Override
    @Transactional(readOnly = true)
    public AddonResponse getByCode(String code) {
        Addon addon = addonRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Addon not found with code: " + code));
        return toResponse(addon);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddonResponse> getByTariffCode(String tariffCode) {
        List<TariffAddon> links = tariffAddonRepository.findByTariff_Code(tariffCode);
        return links.stream().map(link -> toResponse(link.getAddon())).toList();
    }

    private AddonResponse toResponse(Addon a) {
        return new AddonResponse(
                a.getId(),
                a.getCode(),
                a.getName(),
                a.getPrice(),
                CURRENCY,
                a.getType(),
                a.getValidityDays()
        );
    }
}
