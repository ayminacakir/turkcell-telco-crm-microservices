package com.turkcell.notification_service.controller;

import com.turkcell.notification_service.dto.NotificationResponse;
import com.turkcell.notification_service.dto.PageResponse;
import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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

    /** Dokuman bolum 12: ?page=0&size=20&sort=sentAt,desc — Spring Data Pageable. */
    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> getByUserId(
            @RequestParam UUID userId,
            @PageableDefault(size = 20, sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(notificationService.getByUserId(userId, pageable));
    }
}
