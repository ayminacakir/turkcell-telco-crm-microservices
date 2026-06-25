package com.turkcell.order_service.enums;

public enum OrderStatus {
    DRAFT, // Sipariş oluşturuldu ama süreç tamamlanmadı
    PENDING_PAYMENT,
    PAID,
    FULFILLED, // Sipariş tamamlandı, abonelik açılabilir
    CANCELLED// Sipariş iptal edildi
}
