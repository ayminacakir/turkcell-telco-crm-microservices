package com.turkcell.billing_service.service.impl;

import com.turkcell.billing_service.domain.entity.Invoice;
import com.turkcell.billing_service.dto.request.CreateInvoiceRequest;
import com.turkcell.billing_service.dto.response.InvoiceLineResponse;
import com.turkcell.billing_service.dto.response.InvoiceResponse;
import com.turkcell.billing_service.event.PaymentCompletedEvent;
import com.turkcell.billing_service.kafka.entity.ProcessedEvent;
import com.turkcell.billing_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.billing_service.repository.InvoiceLineRepository;
import com.turkcell.billing_service.repository.InvoiceRepository;
import com.turkcell.billing_service.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final ProcessedEventRepository processedEventRepository;

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

    @Override
    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        if (event.eventId() != null && processedEventRepository.existsById(event.eventId())) {
            log.warn("PaymentCompleted event {} already processed, skipping", event.eventId());
            return;
        }

        if (event.invoiceId() == null) {
            // Siparis odemeleri faturaya bagli degildir; guncellenecek fatura yok.
            log.info("PaymentCompleted event {} has no invoiceId (order payment), nothing to update",
                    event.eventId());
        } else {
            invoiceRepository.findById(event.invoiceId()).ifPresentOrElse(
                    invoice -> {
                        if ("COMPLETED".equals(event.status())) {
                            invoice.setStatus("PAID");
                            invoiceRepository.save(invoice);
                            log.info("Invoice {} marked as PAID", event.invoiceId());
                        } else if ("FAILED".equals(event.status())) {
                            invoice.setStatus("OVERDUE");
                            invoiceRepository.save(invoice);
                            log.info("Invoice {} marked as OVERDUE", event.invoiceId());
                        }
                    },
                    () -> log.warn("Invoice not found: {}", event.invoiceId())
            );
        }

        if (event.eventId() != null) {
            processedEventRepository.save(new ProcessedEvent(event.eventId(), "PaymentCompleted"));
        }
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