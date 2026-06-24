package com.turkcell.customer_service.dto.response;

import com.turkcell.customer_service.enums.DocumentType;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID customerId,
        DocumentType type,
        String fileRef,
        LocalDateTime verifiedAt) {
}