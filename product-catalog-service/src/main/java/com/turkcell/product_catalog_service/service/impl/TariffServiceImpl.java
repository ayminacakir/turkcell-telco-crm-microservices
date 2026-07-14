package com.turkcell.product_catalog_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.product_catalog_service.domain.entity.Tariff;
import com.turkcell.product_catalog_service.domain.enums.TariffStatus;
import com.turkcell.product_catalog_service.dto.TariffCreateRequest;
import com.turkcell.product_catalog_service.dto.TariffResponse;
import com.turkcell.product_catalog_service.exception.DuplicateCodeException;
import com.turkcell.product_catalog_service.exception.ResourceNotFoundException;
import com.turkcell.product_catalog_service.outbox.entity.OutboxEvent;
import com.turkcell.product_catalog_service.outbox.enums.OutboxStatus;
import com.turkcell.product_catalog_service.outbox.event.TariffCreatedEvent;
import com.turkcell.product_catalog_service.outbox.event.TariffPriceChangedEvent;
import com.turkcell.product_catalog_service.outbox.repository.OutboxEventRepository;
import com.turkcell.product_catalog_service.repository.TariffRepository;
import com.turkcell.product_catalog_service.service.TariffService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TariffServiceImpl implements TariffService {

    private static final String CURRENCY = "TRY";

    private final TariffRepository tariffRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public TariffServiceImpl(TariffRepository tariffRepository,
                              OutboxEventRepository outboxEventRepository,
                              ObjectMapper objectMapper) {
        this.tariffRepository = tariffRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
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

        Tariff saved = tariffRepository.save(tariff);

        saveOutboxEvent(saved.getId(), "TariffCreated",
                new TariffCreatedEvent(
                        UUID.randomUUID(), "TariffCreated",
                        saved.getId(), saved.getCode(), saved.getName(), saved.getType(),
                        saved.getMonthlyFee(), saved.getMinutesIncluded(),
                        saved.getSmsIncluded(), saved.getDataMbIncluded(),
                        LocalDateTime.now()));

        return toResponse(saved);
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
        LocalDate today = LocalDate.now();
        return tariffRepository.findByStatus(TariffStatus.ACTIVE).stream()
                .filter(t -> isCurrentlyValid(t, today))
                .map(this::toResponse)
                .toList();
    }

    @Override
    public TariffResponse updatePrice(String code, BigDecimal newMonthlyFee) {
        Tariff tariff = tariffRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Tariff not found with code: " + code));

        BigDecimal oldFee = tariff.getMonthlyFee();
        tariff.setMonthlyFee(newMonthlyFee);
        Tariff saved = tariffRepository.save(tariff);

        saveOutboxEvent(saved.getId(), "TariffPriceChanged",
                new TariffPriceChangedEvent(
                        UUID.randomUUID(), "TariffPriceChanged",
                        saved.getId(), saved.getCode(), oldFee, newMonthlyFee,
                        LocalDateTime.now()));

        return toResponse(saved);
    }

    /**
     * Bir tarifenin "gercekten bugun gecerli" olmasi icin status=ACTIVE olmasi yetmez;
     * effectiveFrom/effectiveTo tarih araligina da bugunun dahil olmasi gerekir.
     * effectiveTo null ise sinirsiz gecerlilik anlamina gelir.
     */
    private boolean isCurrentlyValid(Tariff tariff, LocalDate today) {
        boolean startedAlready = tariff.getEffectiveFrom() == null
                || !today.isBefore(tariff.getEffectiveFrom());
        boolean notEndedYet = tariff.getEffectiveTo() == null
                || !today.isAfter(tariff.getEffectiveTo());
        return startedAlready && notEndedYet;
    }

    private void saveOutboxEvent(UUID aggregateId, String eventType, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + eventType + " event", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setAggregateType("TARIFF");
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(json);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEventRepository.save(outboxEvent);
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
