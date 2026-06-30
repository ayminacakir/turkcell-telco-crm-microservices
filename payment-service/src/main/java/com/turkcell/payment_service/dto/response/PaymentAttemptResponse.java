package com.turkcell.payment_service.dto.response;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record PaymentAttemptResponse(
    UUID id,
    UUID paymentId,
    int attemptNo,
    String response,
    LocalDateTime attemptedAt
) {}