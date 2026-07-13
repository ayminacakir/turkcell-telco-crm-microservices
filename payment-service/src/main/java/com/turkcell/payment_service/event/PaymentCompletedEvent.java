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
public class PaymentCompletedEvent {
    private UUID eventId;
    private String eventType;
    private UUID paymentId;
    private UUID orderId;
    private UUID invoiceId;
    private UUID customerId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime paidAt;
    private String tariffCode;
    private Integer minutesIncluded;
    private Integer smsIncluded;
    private Integer dataMbIncluded;
}