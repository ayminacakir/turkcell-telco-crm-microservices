package com.turkcell.order_service.enums;

public enum SagaStatus {
    STARTED, // Saga süreci başladı
    PAYMENT_PENDING,
    PAYMENT_COMPLETED,
    SUBSCRIPTION_PENDING,
    COMPLETED,
    FAILED,
    COMPENSATED // Geri alma işlemi yapıldı
}
