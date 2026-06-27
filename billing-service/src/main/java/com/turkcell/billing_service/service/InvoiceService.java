package com.turkcell.billing_service.service;

import com.turkcell.billing_service.dto.request.CreateInvoiceRequest;
import com.turkcell.billing_service.dto.response.InvoiceLineResponse;
import com.turkcell.billing_service.dto.response.InvoiceResponse;

import java.util.List;
import java.util.UUID;

public interface InvoiceService {
    InvoiceResponse create(CreateInvoiceRequest request);
    InvoiceResponse getById(UUID id);
    List<InvoiceResponse> getByCustomerId(UUID customerId);
    List<InvoiceResponse> getBySubscriptionId(UUID subscriptionId);
    InvoiceResponse updateStatus(UUID id, String status);
    List<InvoiceLineResponse> getLinesByInvoiceId(UUID invoiceId);
}