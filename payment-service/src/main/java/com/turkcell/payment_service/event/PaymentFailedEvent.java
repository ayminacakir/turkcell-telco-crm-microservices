package com.turkcell.payment_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private UUID eventId;
    private String eventType;
    private UUID paymentId;
    private UUID orderId;
    private UUID customerId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String reason;
    private LocalDateTime failedAt;
}