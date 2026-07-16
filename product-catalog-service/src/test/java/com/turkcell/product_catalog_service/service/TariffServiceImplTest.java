package com.turkcell.product_catalog_service.service;

import com.turkcell.product_catalog_service.domain.entity.Tariff;
import com.turkcell.product_catalog_service.domain.entity.TariffVersion;
import com.turkcell.product_catalog_service.domain.enums.TariffStatus;
import com.turkcell.product_catalog_service.domain.enums.TariffType;
import com.turkcell.product_catalog_service.dto.PageResponse;
import com.turkcell.product_catalog_service.dto.TariffCreateRequest;
import com.turkcell.product_catalog_service.dto.TariffResponse;
import com.turkcell.product_catalog_service.exception.DuplicateCodeException;
import com.turkcell.product_catalog_service.exception.ResourceNotFoundException;
import com.turkcell.product_catalog_service.repository.TariffRepository;
import com.turkcell.product_catalog_service.repository.TariffVersionRepository;
import com.turkcell.product_catalog_service.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.product_catalog_service.service.impl.TariffServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    private TariffVersionRepository tariffVersionRepository;

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
                LocalDate.of(2026, 1, 1), null, "GENERAL"
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
                LocalDate.of(2026, 1, 1), null, "GENERAL"
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
    void getActive_shouldQueryValidTariffsForTodayAndMapToPageResponse() {
        // Tarih araligi filtresi artik DB sorgusunda (findValidOn JPQL) yapiliyor;
        // unit test delegasyonu ve mapping'i dogrular.
        Pageable pageable = PageRequest.of(0, 20);
        when(tariffRepository.findValidOn(eq(TariffStatus.ACTIVE), eq(LocalDate.now()), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleTariff), pageable, 1));

        PageResponse<TariffResponse> result = tariffService.getActive(pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo(TariffStatus.ACTIVE);
        assertThat(result.totalElements()).isEqualTo(1);
        verify(tariffRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getAll_shouldReturnPagedResponse() {
        Pageable pageable = PageRequest.of(1, 5);
        when(tariffRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(sampleTariff), pageable, 6));

        PageResponse<TariffResponse> result = tariffService.getAll(pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(6);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void updatePrice_shouldSnapshotOldVersionAndIncrementVersion() {
        sampleTariff.setVersion(1);
        when(tariffRepository.findByCode("POSTPAID_20GB")).thenReturn(Optional.of(sampleTariff));
        when(tariffRepository.save(any(Tariff.class))).thenAnswer(inv -> inv.getArgument(0));

        TariffResponse response = tariffService.updatePrice("POSTPAID_20GB", new BigDecimal("349.90"));

        // FR-08: onceki hal (eski fiyat + eski versiyon no) snapshot'lanmali
        verify(tariffVersionRepository).save(argThat(v ->
                v.getVersion() == 1 && v.getMonthlyFee().compareTo(new BigDecimal("299.90")) == 0));
        assertThat(response.version()).isEqualTo(2);
        assertThat(response.monthlyFee()).isEqualByComparingTo("349.90");
    }

    @Test
    void getVersions_shouldThrowResourceNotFoundException_whenTariffNotExists() {
        when(tariffRepository.existsByCode("UNKNOWN")).thenReturn(false);

        assertThatThrownBy(() -> tariffService.getVersions("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("UNKNOWN");

        verify(tariffVersionRepository, never()).findByCodeOrderByVersionDesc(any());
    }

    @Test
    void getVersions_shouldReturnArchivedVersionsNewestFirst() {
        sampleTariff.setVersion(2);
        TariffVersion archived = TariffVersion.snapshotOf(sampleTariff);
        archived.setVersion(1);
        when(tariffRepository.existsByCode("POSTPAID_20GB")).thenReturn(true);
        when(tariffVersionRepository.findByCodeOrderByVersionDesc("POSTPAID_20GB"))
                .thenReturn(List.of(archived));

        List<TariffResponse> result = tariffService.getVersions("POSTPAID_20GB");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).version()).isEqualTo(1);
    }
}
