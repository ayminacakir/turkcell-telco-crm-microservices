package com.turkcell.order_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerResponse(UUID id, CustomerStatus status) {
}
