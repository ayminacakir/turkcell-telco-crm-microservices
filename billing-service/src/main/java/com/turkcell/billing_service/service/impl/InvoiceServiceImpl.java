package com.turkcell.billing_service.service.impl;

import com.turkcell.billing_service.domain.entity.Invoice;
import com.turkcell.billing_service.dto.request.CreateInvoiceRequest;
import com.turkcell.billing_service.dto.response.InvoiceLineResponse;
import com.turkcell.billing_service.dto.response.InvoiceResponse;
import com.turkcell.billing_service.repository.InvoiceLineRepository;
import com.turkcell.billing_service.repository.InvoiceRepository;
import com.turkcell.billing_service.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;

    @Override
    public InvoiceResponse create(CreateInvoiceRequest request) {
        Invoice invoice = new Invoice();
        invoice.setCustomerId(request.customerId());
        invoice.setSubscriptionId(request.subscriptionId());
        invoice.setPeriodStart(request.periodStart());
        invoice.setPeriodEnd(request.periodEnd());
        invoice.setSubTotal(BigDecimal.ZERO);
        invoice.setTax(BigDecimal.ZERO);
        invoice.setGrandTotal(BigDecimal.ZERO);
        invoice.setStatus("DRAFT");
        invoice.setDueDate(request.periodEnd().plusDays(15));
        Invoice saved = invoiceRepository.save(invoice);
        return toResponse(saved);
    }

    @Override
    public InvoiceResponse getById(UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + id));
        return toResponse(invoice);
    }

    @Override
    public List<InvoiceResponse> getByCustomerId(UUID customerId) {
        return invoiceRepository.findByCustomerId(customerId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<InvoiceResponse> getBySubscriptionId(UUID subscriptionId) {
        return invoiceRepository.findBySubscriptionId(subscriptionId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public InvoiceResponse updateStatus(UUID id, String status) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + id));
        invoice.setStatus(status);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Override
    public List<InvoiceLineResponse> getLinesByInvoiceId(UUID invoiceId) {
        return invoiceLineRepository.findByInvoiceId(invoiceId)
            .stream().map(line -> new InvoiceLineResponse(
                    line.getId(),
                    line.getInvoice().getId(),
                    line.getDescription(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getLineTotal()
            )).collect(Collectors.toList());
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getCustomerId(),
                invoice.getSubscriptionId(),
                invoice.getPeriodStart(),
                invoice.getPeriodEnd(),
                invoice.getSubTotal(),
                invoice.getTax(),
                invoice.getGrandTotal(),
                invoice.getStatus(),
                invoice.getDueDate(),
                invoice.getIssuedAt()
        );
    }
}