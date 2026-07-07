package com.turkcell.payment_service.service;

import com.turkcell.payment_service.dto.request.CreatePaymentRequest;
import com.turkcell.payment_service.dto.response.PaymentAttemptResponse;
import com.turkcell.payment_service.dto.response.PaymentResponse;
import com.turkcell.payment_service.event.OrderCreatedEvent;

import java.util.List;
import java.util.UUID;

public interface PaymentService {
    PaymentResponse createPayment(CreatePaymentRequest request);
    PaymentResponse getById(UUID id);
    List<PaymentResponse> getByInvoiceId(UUID invoiceId);
    PaymentResponse processPayment(UUID id);
    PaymentResponse refundPayment(UUID id);
    List<PaymentAttemptResponse> getAttempts(UUID paymentId);
    void handleOrderCreated(OrderCreatedEvent event);
}