package com.turkcell.usage_service.service;

import com.turkcell.usage_service.dto.request.CreateQuotaRequest;
import com.turkcell.usage_service.dto.request.CreateUsageRecordRequest;
import com.turkcell.usage_service.dto.response.QuotaResponse;
import com.turkcell.usage_service.dto.response.UsageRecordResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface UsageService {
    UsageRecordResponse recordUsage(CreateUsageRecordRequest request);
    List<UsageRecordResponse> getHistory(UUID subscriptionId, LocalDateTime from, LocalDateTime to);
    QuotaResponse getActiveQuota(UUID subscriptionId);
    QuotaResponse createQuota(CreateQuotaRequest request);
}