package com.turkcell.customer_service.dto.request;

import com.turkcell.customer_service.enums.DocumentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDocumentRequest(
        @NotNull DocumentType type,

        @NotBlank String fileRef) {

}
