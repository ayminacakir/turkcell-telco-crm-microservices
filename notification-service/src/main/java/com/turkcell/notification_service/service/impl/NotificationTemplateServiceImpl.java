package com.turkcell.notification_service.service.impl;

import com.turkcell.notification_service.domain.entity.NotificationTemplate;
import com.turkcell.notification_service.dto.NotificationTemplateCreateRequest;
import com.turkcell.notification_service.dto.NotificationTemplateResponse;
import com.turkcell.notification_service.exception.DuplicateCodeException;
import com.turkcell.notification_service.exception.ResourceNotFoundException;
import com.turkcell.notification_service.repository.NotificationTemplateRepository;
import com.turkcell.notification_service.service.NotificationTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class NotificationTemplateServiceImpl implements NotificationTemplateService {

    private final NotificationTemplateRepository templateRepository;

    public NotificationTemplateServiceImpl(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    public NotificationTemplateResponse create(NotificationTemplateCreateRequest request) {
        if (templateRepository.existsByCode(request.code())) {
            throw new DuplicateCodeException("Template with code " + request.code() + " already exists");
        }

        NotificationTemplate template = new NotificationTemplate();
        template.setCode(request.code());
        template.setChannel(request.channel());
        template.setLocale(request.locale());
        template.setSubject(request.subject());
        template.setBodyTemplate(request.bodyTemplate());

        return toResponse(templateRepository.save(template));
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationTemplateResponse getByCode(String code) {
        return toResponse(findEntityByCode(code));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationTemplateResponse> getAll() {
        return templateRepository.findAll().stream().map(this::toResponse).toList();
    }

    private NotificationTemplate findEntityByCode(String code) {
        return templateRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with code: " + code));
    }

    private NotificationTemplateResponse toResponse(NotificationTemplate t) {
        return new NotificationTemplateResponse(
                t.getId(), t.getCode(), t.getChannel(), t.getLocale(), t.getSubject(), t.getBodyTemplate()
        );
    }
}
