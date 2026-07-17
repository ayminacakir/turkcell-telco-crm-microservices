package com.turkcell.notification_service.service;

import com.turkcell.notification_service.dto.NotificationPreferenceResponse;
import com.turkcell.notification_service.dto.UpdatePreferenceRequest;

import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceService {

    NotificationPreferenceResponse updatePreference(UUID userId, UpdatePreferenceRequest request);

    List<NotificationPreferenceResponse> getPreferences(UUID userId);
}
