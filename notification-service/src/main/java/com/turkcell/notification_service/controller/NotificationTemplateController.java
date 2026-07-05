package com.turkcell.notification_service.controller;

import com.turkcell.notification_service.dto.NotificationTemplateCreateRequest;
import com.turkcell.notification_service.dto.NotificationTemplateResponse;
import com.turkcell.notification_service.service.NotificationTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notification-templates")
public class NotificationTemplateController {

    private final NotificationTemplateService templateService;

    public NotificationTemplateController(NotificationTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    public ResponseEntity<NotificationTemplateResponse> create(
            @Valid @RequestBody NotificationTemplateCreateRequest request) {
        NotificationTemplateResponse created = templateService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/notification-templates/" + created.code()))
                .body(created);
    }

    @GetMapping("/{code}")
    public ResponseEntity<NotificationTemplateResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(templateService.getByCode(code));
    }

    @GetMapping
    public ResponseEntity<List<NotificationTemplateResponse>> getAll() {
        return ResponseEntity.ok(templateService.getAll());
    }
}
