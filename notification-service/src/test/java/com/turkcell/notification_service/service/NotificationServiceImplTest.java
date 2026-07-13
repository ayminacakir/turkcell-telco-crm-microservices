package com.turkcell.notification_service.service;

import com.turkcell.notification_service.domain.entity.Notification;
import com.turkcell.notification_service.domain.entity.NotificationTemplate;
import com.turkcell.notification_service.domain.enums.NotificationChannel;
import com.turkcell.notification_service.domain.enums.NotificationStatus;
import com.turkcell.notification_service.dto.NotificationResponse;
import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.exception.ResourceNotFoundException;
import com.turkcell.notification_service.repository.NotificationRepository;
import com.turkcell.notification_service.repository.NotificationTemplateRepository;
import com.turkcell.notification_service.service.impl.NotificationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationTemplateRepository templateRepository;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(notificationRepository, templateRepository, new ObjectMapper());
    }

    @Test
    void send_shouldRenderTemplateAndPersistAsSent() {
        UUID userId = UUID.randomUUID();
        NotificationTemplate template = new NotificationTemplate();
        template.setId(UUID.randomUUID());
        template.setCode("PAYMENT_RECEIVED");
        template.setChannel(NotificationChannel.SMS);
        template.setLocale("tr-TR");
        template.setBodyTemplate("Sayin musterimiz, {{amount}} {{currency}} tutarindaki odemeniz alinmistir.");

        when(templateRepository.findByCode("PAYMENT_RECEIVED")).thenReturn(Optional.of(template));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        SendNotificationRequest request = new SendNotificationRequest(
                userId, "PAYMENT_RECEIVED", Map.of("amount", "250.00", "currency", "TRY"));

        NotificationResponse response = notificationService.send(request);

        assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(response.channel()).isEqualTo(NotificationChannel.SMS);
        assertThat(response.userId()).isEqualTo(userId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getPayloadJson()).contains("250.00").contains("TRY");
    }

    @Test
    void send_shouldThrowResourceNotFoundException_whenTemplateMissing() {
        when(templateRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        SendNotificationRequest request = new SendNotificationRequest(
                UUID.randomUUID(), "UNKNOWN", Map.of());

        assertThatThrownBy(() -> notificationService.send(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void send_shouldWorkWithoutPlaceholders() {
        NotificationTemplate template = new NotificationTemplate();
        template.setCode("QUOTA_EXCEEDED");
        template.setChannel(NotificationChannel.SMS);
        template.setBodyTemplate("Kotanizi doldurdunuz.");

        when(templateRepository.findByCode("QUOTA_EXCEEDED")).thenReturn(Optional.of(template));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        SendNotificationRequest request = new SendNotificationRequest(UUID.randomUUID(), "QUOTA_EXCEEDED", null);

        NotificationResponse response = notificationService.send(request);

        assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
    }
}
