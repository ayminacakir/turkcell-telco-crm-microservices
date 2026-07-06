package com.turkcell.ticket_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TicketCommentCreateRequest(
        @NotNull UUID authorId,
        @NotBlank String body
) {
}
