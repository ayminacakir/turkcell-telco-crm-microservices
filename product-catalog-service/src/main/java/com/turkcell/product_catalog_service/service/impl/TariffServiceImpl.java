package com.turkcell.product_catalog_service.service.impl;

import com.turkcell.product_catalog_service.domain.entity.Tariff;
import com.turkcell.product_catalog_service.domain.enums.TariffStatus;
import com.turkcell.product_catalog_service.dto.TariffCreateRequest;
import com.turkcell.product_catalog_service.dto.TariffResponse;
import com.turkcell.product_catalog_service.exception.DuplicateCodeException;
import com.turkcell.product_catalog_service.exception.ResourceNotFoundException;
import com.turkcell.product_catalog_service.repository.TariffRepository;
import com.turkcell.product_catalog_service.service.TariffService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TariffServiceImpl implements TariffService {

    private static final String CURRENCY = "TRY";

    private final TariffRepository tariffRepository;

    public TariffServiceImpl(TariffRepository tariffRepository) {
        this.tariffRepository = tariffRepository;
    }

    @Override
    public TariffResponse create(TariffCreateRequest request) {
        if (tariffRepository.existsByCode(request.code())) {
            throw new DuplicateCodeException("Tariff with code " + request.code() + " already exists");
        }

        Tariff tariff = new Tariff();
        tariff.setCode(request.code());
        tariff.setName(request.name());
        tariff.setType(request.type());
        tariff.setMonthlyFee(request.monthlyFee());
        tariff.setMinutesIncluded(request.minutesIncluded());
        tariff.setSmsIncluded(request.smsIncluded());
        tariff.setDataMbIncluded(request.dataMbIncluded());
        tariff.setStatus(TariffStatus.ACTIVE);
        tariff.setEffectiveFrom(request.effectiveFrom());
        tariff.setEffectiveTo(request.effectiveTo());

        return toResponse(tariffRepository.save(tariff));
    }

    @Override
    @Transactional(readOnly = true)
    public TariffResponse getByCode(String code) {
        Tariff tariff = tariffRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Tariff not found with code: " + code));
        return toResponse(tariff);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TariffResponse> getAll() {
        return tariffRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TariffResponse> getActive() {
        return tariffRepository.findByStatus(TariffStatus.ACTIVE).stream()
                .map(this::toResponse)
                .toList();
    }

    private TariffResponse toResponse(Tariff t) {
        return new TariffResponse(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getType(),
                t.getMonthlyFee(),
                CURRENCY,
                t.getMinutesIncluded(),
                t.getSmsIncluded(),
                t.getDataMbIncluded(),
                t.getStatus(),
                t.getEffectiveFrom(),
                t.getEffectiveTo()
        );
    }
}
