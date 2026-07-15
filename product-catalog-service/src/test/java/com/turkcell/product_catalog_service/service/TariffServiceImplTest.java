package com.turkcell.product_catalog_service.service;

import com.turkcell.product_catalog_service.domain.entity.Tariff;
import com.turkcell.product_catalog_service.domain.enums.TariffStatus;
import com.turkcell.product_catalog_service.domain.enums.TariffType;
import com.turkcell.product_catalog_service.dto.TariffCreateRequest;
import com.turkcell.product_catalog_service.dto.TariffResponse;
import com.turkcell.product_catalog_service.exception.DuplicateCodeException;
import com.turkcell.product_catalog_service.exception.ResourceNotFoundException;
import com.turkcell.product_catalog_service.repository.TariffRepository;
import com.turkcell.product_catalog_service.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.product_catalog_service.service.impl.TariffServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TariffServiceImplTest {

    @Mock
    private TariffRepository tariffRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TariffServiceImpl tariffService;

    private Tariff sampleTariff;

    @BeforeEach
    void setUp() {
        sampleTariff = new Tariff();
        sampleTariff.setId(UUID.randomUUID());
        sampleTariff.setCode("POSTPAID_20GB");
        sampleTariff.setName("20GB Postpaid Paket");
        sampleTariff.setType(TariffType.POSTPAID);
        sampleTariff.setMonthlyFee(new BigDecimal("299.90"));
        sampleTariff.setMinutesIncluded(1000);
        sampleTariff.setSmsIncluded(500);
        sampleTariff.setDataMbIncluded(20000);
        sampleTariff.setStatus(TariffStatus.ACTIVE);
        sampleTariff.setEffectiveFrom(LocalDate.of(2026, 1, 1));
    }

    @Test
    void create_shouldSaveAndReturnResponse_whenCodeIsUnique() {
        TariffCreateRequest request = new TariffCreateRequest(
                "POSTPAID_20GB", "20GB Postpaid Paket", TariffType.POSTPAID,
                new BigDecimal("299.90"), 1000, 500, 20000,
                LocalDate.of(2026, 1, 1), null
        );
        when(tariffRepository.existsByCode("POSTPAID_20GB")).thenReturn(false);
        when(tariffRepository.save(any(Tariff.class))).thenReturn(sampleTariff);

        TariffResponse response = tariffService.create(request);

        assertThat(response.code()).isEqualTo("POSTPAID_20GB");
        assertThat(response.status()).isEqualTo(TariffStatus.ACTIVE);
        assertThat(response.currency()).isEqualTo("TRY");
        verify(tariffRepository).save(any(Tariff.class));
    }

    @Test
    void create_shouldThrowDuplicateCodeException_whenCodeAlreadyExists() {
        TariffCreateRequest request = new TariffCreateRequest(
                "POSTPAID_20GB", "20GB Postpaid Paket", TariffType.POSTPAID,
                new BigDecimal("299.90"), 1000, 500, 20000,
                LocalDate.of(2026, 1, 1), null
        );
        when(tariffRepository.existsByCode("POSTPAID_20GB")).thenReturn(true);

        assertThatThrownBy(() -> tariffService.create(request))
                .isInstanceOf(DuplicateCodeException.class)
                .hasMessageContaining("POSTPAID_20GB");

        verify(tariffRepository, never()).save(any());
    }

    @Test
    void getByCode_shouldReturnTariff_whenExists() {
        when(tariffRepository.findByCode("POSTPAID_20GB")).thenReturn(Optional.of(sampleTariff));

        TariffResponse response = tariffService.getByCode("POSTPAID_20GB");

        assertThat(response.code()).isEqualTo("POSTPAID_20GB");
        assertThat(response.monthlyFee()).isEqualByComparingTo("299.90");
    }

    @Test
    void getByCode_shouldThrowResourceNotFoundException_whenNotExists() {
        when(tariffRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tariffService.getByCode("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void getActive_shouldReturnOnlyActiveTariffs() {
        when(tariffRepository.findByStatus(TariffStatus.ACTIVE)).thenReturn(List.of(sampleTariff));

        List<TariffResponse> result = tariffService.getActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(TariffStatus.ACTIVE);
        verify(tariffRepository).findByStatus(TariffStatus.ACTIVE);
        verify(tariffRepository, never()).findAll();
    }

    @Test
    void getActive_shouldExcludeTariff_whenEffectiveFromIsInTheFuture() {
        sampleTariff.setEffectiveFrom(LocalDate.now().plusDays(30));
        when(tariffRepository.findByStatus(TariffStatus.ACTIVE)).thenReturn(List.of(sampleTariff));

        List<TariffResponse> result = tariffService.getActive();

        assertThat(result).isEmpty();
    }

    @Test
    void getActive_shouldExcludeTariff_whenEffectiveToIsInThePast() {
        sampleTariff.setEffectiveFrom(LocalDate.now().minusDays(60));
        sampleTariff.setEffectiveTo(LocalDate.now().minusDays(1));
        when(tariffRepository.findByStatus(TariffStatus.ACTIVE)).thenReturn(List.of(sampleTariff));

        List<TariffResponse> result = tariffService.getActive();

        assertThat(result).isEmpty();
    }

    @Test
    void getActive_shouldIncludeTariff_whenEffectiveToIsNull() {
        sampleTariff.setEffectiveFrom(LocalDate.now().minusDays(1));
        sampleTariff.setEffectiveTo(null);
        when(tariffRepository.findByStatus(TariffStatus.ACTIVE)).thenReturn(List.of(sampleTariff));

        List<TariffResponse> result = tariffService.getActive();

        assertThat(result).hasSize(1);
    }
}
