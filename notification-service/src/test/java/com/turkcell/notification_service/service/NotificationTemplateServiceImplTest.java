package com.turkcell.notification_service.service;

import com.turkcell.notification_service.domain.entity.NotificationTemplate;
import com.turkcell.notification_service.domain.enums.NotificationChannel;
import com.turkcell.notification_service.dto.NotificationTemplateCreateRequest;
import com.turkcell.notification_service.dto.NotificationTemplateResponse;
import com.turkcell.notification_service.exception.DuplicateCodeException;
import com.turkcell.notification_service.exception.ResourceNotFoundException;
import com.turkcell.notification_service.repository.NotificationTemplateRepository;
import com.turkcell.notification_service.service.impl.NotificationTemplateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationTemplateServiceImplTest {

    @Mock
    private NotificationTemplateRepository templateRepository;

    @InjectMocks
    private NotificationTemplateServiceImpl templateService;

    private NotificationTemplate sampleTemplate;

    @BeforeEach
    void setUp() {
        sampleTemplate = new NotificationTemplate();
        sampleTemplate.setId(UUID.randomUUID());
        sampleTemplate.setCode("PAYMENT_RECEIVED");
        sampleTemplate.setChannel(NotificationChannel.SMS);
        sampleTemplate.setLocale("tr-TR");
        sampleTemplate.setBodyTemplate("Odemeniz alinmistir: {{amount}} {{currency}}");
    }

    @Test
    void create_shouldSaveAndReturnResponse_whenCodeIsUnique() {
        NotificationTemplateCreateRequest request = new NotificationTemplateCreateRequest(
                "PAYMENT_RECEIVED", NotificationChannel.SMS, "tr-TR", null,
                "Odemeniz alinmistir: {{amount}} {{currency}}");
        when(templateRepository.existsByCode("PAYMENT_RECEIVED")).thenReturn(false);
        when(templateRepository.save(any(NotificationTemplate.class))).thenReturn(sampleTemplate);

        NotificationTemplateResponse response = templateService.create(request);

        assertThat(response.code()).isEqualTo("PAYMENT_RECEIVED");
        assertThat(response.channel()).isEqualTo(NotificationChannel.SMS);
    }

    @Test
    void create_shouldThrowDuplicateCodeException_whenCodeAlreadyExists() {
        NotificationTemplateCreateRequest request = new NotificationTemplateCreateRequest(
                "PAYMENT_RECEIVED", NotificationChannel.SMS, "tr-TR", null, "body");
        when(templateRepository.existsByCode("PAYMENT_RECEIVED")).thenReturn(true);

        assertThatThrownBy(() -> templateService.create(request))
                .isInstanceOf(DuplicateCodeException.class);

        verify(templateRepository, never()).save(any());
    }

    @Test
    void getByCode_shouldReturnTemplate_whenExists() {
        when(templateRepository.findByCode("PAYMENT_RECEIVED")).thenReturn(Optional.of(sampleTemplate));

        NotificationTemplateResponse response = templateService.getByCode("PAYMENT_RECEIVED");

        assertThat(response.bodyTemplate()).contains("{{amount}}");
    }

    @Test
    void getByCode_shouldThrowResourceNotFoundException_whenNotExists() {
        when(templateRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.getByCode("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAll_shouldReturnAllTemplates() {
        when(templateRepository.findAll()).thenReturn(List.of(sampleTemplate));

        List<NotificationTemplateResponse> result = templateService.getAll();

        assertThat(result).hasSize(1);
    }
}
