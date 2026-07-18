package com.turkcell.usage_service.dto.request;

/** Tarife değişince aktif kotayı yeni paketin dahil miktarlarına sıfırlar. */
public record ResetQuotaRequest(Integer minutes, Integer sms, Integer mb) {}
