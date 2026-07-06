package com.turkcell.notification_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.notification_service.domain.entity.Notification;
import com.turkcell.notification_service.domain.entity.NotificationTemplate;
import com.turkcell.notification_service.domain.enums.NotificationStatus;
import com.turkcell.notification_service.dto.NotificationResponse;
import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.exception.ResourceNotFoundException;
import com.turkcell.notification_service.repository.NotificationRepository;
import com.turkcell.notification_service.repository.NotificationTemplateRepository;
import com.turkcell.notification_service.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                    NotificationTemplateRepository templateRepository) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public NotificationResponse send(SendNotificationRequest request) {
        NotificationTemplate template = templateRepository.findByCode(request.templateCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template not found with code: " + request.templateCode()));

        String renderedBody = render(template.getBodyTemplate(), request.placeholders());

        Notification notification = new Notification();
        notification.setUserId(request.userId());
        notification.setTemplateCode(template.getCode());
        notification.setChannel(template.getChannel());
        notification.setPayloadJson(toJson(request.placeholders()));
        notification.setStatus(NotificationStatus.PENDING);

        // MVP: gerçek SMS/e-posta/push gönderimi yok, mock gönderim ile SENT'e çekiliyor.
        // İleride burası SmsSenderClient / EmailSenderClient gibi bir port'a devredilecek.
        log.info("Sending {} notification to userId={} via template={}: {}",
                template.getChannel(), request.userId(), template.getCode(), renderedBody);
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());

        return toResponse(notificationRepository.save(notification));
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationResponse getById(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        return toResponse(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getByUserId(UUID userId) {
        return notificationRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    private String render(String bodyTemplate, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return bodyTemplate;
        }
        String result = bodyTemplate;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private String toJson(Map<String, String> placeholders) {
        try {
            return objectMapper.writeValueAsString(placeholders == null ? Map.of() : placeholders);
        } catch (JsonProcessingException e) {
            log.warn("Placeholders JSON'a çevrilemedi, boş obje kaydediliyor", e);
            return "{}";
        }
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getUserId(), n.getTemplateCode(), n.getChannel(), n.getStatus(), n.getSentAt()
        );
    }
}
