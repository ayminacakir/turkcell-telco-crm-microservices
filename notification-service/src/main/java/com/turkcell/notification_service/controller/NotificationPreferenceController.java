package com.turkcell.notification_service.controller;

import com.turkcell.notification_service.dto.NotificationPreferenceResponse;
import com.turkcell.notification_service.dto.UpdatePreferenceRequest;
import com.turkcell.notification_service.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * FR-30: Kullanicinin iletisim tercihlerine (opt-in/opt-out) saygi gosterilir.
 */
@RestController
@RequestMapping("/api/v1/notifications/preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @PutMapping("/{userId}")
    public ResponseEntity<NotificationPreferenceResponse> updatePreference(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdatePreferenceRequest request) {
        return ResponseEntity.ok(preferenceService.updatePreference(userId, request));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<NotificationPreferenceResponse>> getPreferences(@PathVariable UUID userId) {
        return ResponseEntity.ok(preferenceService.getPreferences(userId));
    }
}
