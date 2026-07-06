package com.turkcell.notification_service.service;

import com.turkcell.notification_service.dto.NotificationTemplateCreateRequest;
import com.turkcell.notification_service.dto.NotificationTemplateResponse;

import java.util.List;

public interface NotificationTemplateService {

    NotificationTemplateResponse create(NotificationTemplateCreateRequest request);

    NotificationTemplateResponse getByCode(String code);

    List<NotificationTemplateResponse> getAll();
}
