package com.turkcell.payment_service.controller;

import com.turkcell.payment_service.dto.request.CreatePaymentRequest;
import com.turkcell.payment_service.dto.response.PaymentAttemptResponse;
import com.turkcell.payment_service.dto.response.PaymentResponse;
import com.turkcell.payment_service.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPayment(request, idempotencyKey));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getById(id));
    }

    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<List<PaymentResponse>> getByInvoice(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(paymentService.getByInvoiceId(invoiceId));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<PaymentResponse> process(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.processPayment(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refund(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.refundPayment(id));
    }

    @GetMapping("/{id}/attempts")
    public ResponseEntity<List<PaymentAttemptResponse>> getAttempts(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getAttempts(id));
    }
}