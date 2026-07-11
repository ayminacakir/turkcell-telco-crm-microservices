package com.turkcell.billing_service.controller;

import com.turkcell.billing_service.dto.request.CreateInvoiceRequest;
import com.turkcell.billing_service.dto.response.InvoiceLineResponse;
import com.turkcell.billing_service.dto.response.InvoiceResponse;
import com.turkcell.billing_service.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody CreateInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.getById(id));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<InvoiceResponse>> getByCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok(invoiceService.getByCustomerId(customerId));
    }

    @GetMapping("/subscription/{subscriptionId}")
    public ResponseEntity<List<InvoiceResponse>> getBySubscription(@PathVariable UUID subscriptionId) {
        return ResponseEntity.ok(invoiceService.getBySubscriptionId(subscriptionId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<InvoiceResponse> updateStatus(@PathVariable UUID id,
                                                         @RequestParam String status) {
        return ResponseEntity.ok(invoiceService.updateStatus(id, status));
    }

    @GetMapping("/{id}/lines")
    public ResponseEntity<List<InvoiceLineResponse>> getLines(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.getLinesByInvoiceId(id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getPdf(@PathVariable UUID id) {
        byte[] pdf = invoiceService.generatePdf(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=invoice-" + id + ".pdf")
                .body(pdf);
    }
}