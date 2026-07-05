package com.turkcell.notification_service.controller;

import com.turkcell.notification_service.dto.NotificationResponse;
import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Manuel tetikleme endpoint'i. Kafka consumer'lar bağlanana kadar
     * (quota.threshold.reached, payment.completed vb.) test amaçlı kullanılır;
     * consumer'lar da event geldiğinde aynı NotificationService.send() metodunu çağıracak.
     */
    @PostMapping("/send")
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody SendNotificationRequest request) {
        NotificationResponse sent = notificationService.send(request);
        return ResponseEntity.created(URI.create("/api/v1/notifications/" + sent.id())).body(sent);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getByUserId(@RequestParam UUID userId) {
        return ResponseEntity.ok(notificationService.getByUserId(userId));
    }
}
