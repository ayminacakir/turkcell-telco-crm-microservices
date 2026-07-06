package com.turkcell.product_catalog_service.service;

import com.turkcell.product_catalog_service.domain.entity.Addon;
import com.turkcell.product_catalog_service.domain.entity.Tariff;
import com.turkcell.product_catalog_service.domain.entity.TariffAddonId;
import com.turkcell.product_catalog_service.domain.enums.AddonType;
import com.turkcell.product_catalog_service.dto.AddonCreateRequest;
import com.turkcell.product_catalog_service.dto.AddonResponse;
import com.turkcell.product_catalog_service.exception.DuplicateCodeException;
import com.turkcell.product_catalog_service.exception.ResourceNotFoundException;
import com.turkcell.product_catalog_service.repository.AddonRepository;
import com.turkcell.product_catalog_service.repository.TariffAddonRepository;
import com.turkcell.product_catalog_service.repository.TariffRepository;
import com.turkcell.product_catalog_service.service.impl.AddonServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddonServiceImplTest {

    @Mock
    private AddonRepository addonRepository;

    @Mock
    private TariffRepository tariffRepository;

    @Mock
    private TariffAddonRepository tariffAddonRepository;

    @InjectMocks
    private AddonServiceImpl addonService;

    private Addon sampleAddon;
    private Tariff sampleTariff;

    @BeforeEach
    void setUp() {
        sampleAddon = new Addon();
        sampleAddon.setId(UUID.randomUUID());
        sampleAddon.setCode("EXTRA_5GB");
        sampleAddon.setName("5GB Ek Paket");
        sampleAddon.setPrice(new BigDecimal("49.90"));
        sampleAddon.setType(AddonType.DATA);
        sampleAddon.setValidityDays(30);

        sampleTariff = new Tariff();
        sampleTariff.setId(UUID.randomUUID());
        sampleTariff.setCode("POSTPAID_20GB");
    }

    @Test
    void create_shouldSaveAndReturnResponse_whenCodeIsUnique() {
        AddonCreateRequest request = new AddonCreateRequest(
                "EXTRA_5GB", "5GB Ek Paket", new BigDecimal("49.90"), AddonType.DATA, 30);
        when(addonRepository.existsByCode("EXTRA_5GB")).thenReturn(false);
        when(addonRepository.save(any(Addon.class))).thenReturn(sampleAddon);

        AddonResponse response = addonService.create(request);

        assertThat(response.code()).isEqualTo("EXTRA_5GB");
        assertThat(response.currency()).isEqualTo("TRY");
    }

    @Test
    void create_shouldThrowDuplicateCodeException_whenCodeAlreadyExists() {
        AddonCreateRequest request = new AddonCreateRequest(
                "EXTRA_5GB", "5GB Ek Paket", new BigDecimal("49.90"), AddonType.DATA, 30);
        when(addonRepository.existsByCode("EXTRA_5GB")).thenReturn(true);

        assertThatThrownBy(() -> addonService.create(request))
                .isInstanceOf(DuplicateCodeException.class);

        verify(addonRepository, never()).save(any());
    }

    @Test
    void getByCode_shouldThrowResourceNotFoundException_whenNotExists() {
        when(addonRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addonService.getByCode("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void linkToTariff_shouldCreateLink_whenNotAlreadyLinked() {
        when(tariffRepository.findByCode("POSTPAID_20GB")).thenReturn(Optional.of(sampleTariff));
        when(addonRepository.findByCode("EXTRA_5GB")).thenReturn(Optional.of(sampleAddon));
        when(tariffAddonRepository.existsById(any(TariffAddonId.class))).thenReturn(false);

        addonService.linkToTariff("POSTPAID_20GB", "EXTRA_5GB");

        verify(tariffAddonRepository).save(any());
    }

    @Test
    void linkToTariff_shouldBeIdempotent_whenAlreadyLinked() {
        when(tariffRepository.findByCode("POSTPAID_20GB")).thenReturn(Optional.of(sampleTariff));
        when(addonRepository.findByCode("EXTRA_5GB")).thenReturn(Optional.of(sampleAddon));
        when(tariffAddonRepository.existsById(any(TariffAddonId.class))).thenReturn(true);

        addonService.linkToTariff("POSTPAID_20GB", "EXTRA_5GB");

        verify(tariffAddonRepository, never()).save(any());
    }

    @Test
    void linkToTariff_shouldThrowResourceNotFoundException_whenTariffMissing() {
        when(tariffRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addonService.linkToTariff("UNKNOWN", "EXTRA_5GB"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(addonRepository, never()).findByCode(any());
    }

    @Test
    void unlinkFromTariff_shouldDelete_whenLinkExists() {
        when(tariffRepository.findByCode("POSTPAID_20GB")).thenReturn(Optional.of(sampleTariff));
        when(addonRepository.findByCode("EXTRA_5GB")).thenReturn(Optional.of(sampleAddon));
        when(tariffAddonRepository.existsById(any(TariffAddonId.class))).thenReturn(true);

        addonService.unlinkFromTariff("POSTPAID_20GB", "EXTRA_5GB");

        verify(tariffAddonRepository).deleteById(any(TariffAddonId.class));
    }

    @Test
    void unlinkFromTariff_shouldThrowResourceNotFoundException_whenLinkDoesNotExist() {
        when(tariffRepository.findByCode("POSTPAID_20GB")).thenReturn(Optional.of(sampleTariff));
        when(addonRepository.findByCode("EXTRA_5GB")).thenReturn(Optional.of(sampleAddon));
        when(tariffAddonRepository.existsById(any(TariffAddonId.class))).thenReturn(false);

        assertThatThrownBy(() -> addonService.unlinkFromTariff("POSTPAID_20GB", "EXTRA_5GB"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tariffAddonRepository, never()).deleteById(any(TariffAddonId.class));
    }
}
