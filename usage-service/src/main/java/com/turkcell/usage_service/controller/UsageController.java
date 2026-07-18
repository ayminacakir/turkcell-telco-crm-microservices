package com.turkcell.usage_service.controller;

import com.turkcell.usage_service.dto.request.CreateQuotaRequest;
import com.turkcell.usage_service.dto.request.ResetQuotaRequest;
import com.turkcell.usage_service.dto.request.CreateUsageRecordRequest;
import com.turkcell.usage_service.dto.response.QuotaResponse;
import com.turkcell.usage_service.dto.response.UsageRecordResponse;
import com.turkcell.usage_service.service.UsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/usage")
@RequiredArgsConstructor
public class UsageController {

    private final UsageService usageService;

    // FR-17: Kullanım kaydı oluştur
    @PostMapping("/records")
    public ResponseEntity<UsageRecordResponse> recordUsage(
            @Valid @RequestBody CreateUsageRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(usageService.recordUsage(request));
    }

    // FR-18: Kullanım geçmişi
    @GetMapping("/subscriptions/{subscriptionId}/history")
    public ResponseEntity<List<UsageRecordResponse>> getHistory(
            @PathVariable UUID subscriptionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(usageService.getHistory(subscriptionId, from, to));
    }

    // FR-18: Aktif kota görüntüle
    @GetMapping("/subscriptions/{subscriptionId}/quota")
    public ResponseEntity<QuotaResponse> getActiveQuota(@PathVariable UUID subscriptionId) {
        return ResponseEntity.ok(usageService.getActiveQuota(subscriptionId));
    }

    // Kota oluştur
    // Tarife degisince portal bunu cagirir: kotayi yeni paket miktarina sifirlar.
    @org.springframework.web.bind.annotation.PutMapping("/subscriptions/{subscriptionId}/quota")
    public ResponseEntity<com.turkcell.usage_service.dto.response.QuotaResponse> resetQuota(
            @PathVariable java.util.UUID subscriptionId,
            @RequestBody ResetQuotaRequest req) {
        return ResponseEntity.ok(usageService.resetQuota(subscriptionId,
                req.minutes()==null?0:req.minutes(), req.sms()==null?0:req.sms(), req.mb()==null?0:req.mb()));
    }

    @PostMapping("/quotas")
    public ResponseEntity<QuotaResponse> createQuota(
            @Valid @RequestBody CreateQuotaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(usageService.createQuota(request));
    }
}