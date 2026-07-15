package com.turkcell.notification_service.service.impl;

import com.turkcell.notification_service.domain.entity.NotificationPreference;
import com.turkcell.notification_service.dto.NotificationPreferenceResponse;
import com.turkcell.notification_service.dto.UpdatePreferenceRequest;
import com.turkcell.notification_service.repository.NotificationPreferenceRepository;
import com.turkcell.notification_service.service.NotificationPreferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreferenceServiceImpl(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    @Override
    public NotificationPreferenceResponse updatePreference(UUID userId, UpdatePreferenceRequest request) {
        NotificationPreference preference = preferenceRepository
                .findByUserIdAndChannel(userId, request.channel())
                .orElseGet(() -> {
                    NotificationPreference p = new NotificationPreference();
                    p.setUserId(userId);
                    p.setChannel(request.channel());
                    return p;
                });

        preference.setOptedOut(request.optedOut());
        NotificationPreference saved = preferenceRepository.save(preference);

        return new NotificationPreferenceResponse(saved.getUserId(), saved.getChannel(), saved.isOptedOut());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getPreferences(UUID userId) {
        return preferenceRepository.findByUserId(userId).stream()
                .map(p -> new NotificationPreferenceResponse(p.getUserId(), p.getChannel(), p.isOptedOut()))
                .toList();
    }
}
