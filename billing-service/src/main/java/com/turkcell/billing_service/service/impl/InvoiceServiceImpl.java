package com.turkcell.billing_service.service.impl;

import com.turkcell.billing_service.domain.entity.Invoice;
import com.turkcell.billing_service.dto.request.CreateInvoiceRequest;
import com.turkcell.billing_service.dto.response.InvoiceLineResponse;
import com.turkcell.billing_service.dto.response.InvoiceResponse;
import com.turkcell.billing_service.event.InvoiceOverdueEvent;
import com.turkcell.billing_service.event.InvoicePaidEvent;
import com.turkcell.billing_service.event.PaymentCompletedEvent;
import com.turkcell.billing_service.kafka.entity.ProcessedEvent;
import com.turkcell.billing_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.billing_service.outbox.service.OutboxEventWriter;
import com.turkcell.billing_service.repository.InvoiceLineRepository;
import com.turkcell.billing_service.repository.InvoiceRepository;
import com.turkcell.billing_service.service.InvoicePdfService;
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

    private static final String INVOICE_PAID_EVENT = "InvoicePaid";
    private static final String INVOICE_OVERDUE_EVENT = "InvoiceOverdue";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final InvoicePdfService invoicePdfService;

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
                            publishInvoicePaid(invoice, event.amount());
                            log.info("Invoice {} marked as PAID", event.invoiceId());
                        } else if ("FAILED".equals(event.status())) {
                            invoice.setStatus("OVERDUE");
                            invoiceRepository.save(invoice);
                            publishInvoiceOverdue(invoice);
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

    @Override
    public byte[] generatePdf(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        var lines = invoiceLineRepository.findByInvoiceId(invoiceId);
        return invoicePdfService.generate(invoice, lines);
    }

    private void publishInvoicePaid(Invoice invoice, java.math.BigDecimal amountPaid) {
        java.math.BigDecimal paid = amountPaid != null ? amountPaid : invoice.getGrandTotal();
        InvoicePaidEvent event = new InvoicePaidEvent(
                UUID.randomUUID(),
                INVOICE_PAID_EVENT,
                invoice.getId(),
                invoice.getCustomerId(),
                invoice.getSubscriptionId(),
                paid,
                java.time.LocalDate.now()
        );
        outboxEventWriter.write(invoice.getId(), "Invoice", INVOICE_PAID_EVENT, event);
    }

    private void publishInvoiceOverdue(Invoice invoice) {
        InvoiceOverdueEvent event = new InvoiceOverdueEvent(
                UUID.randomUUID(),
                INVOICE_OVERDUE_EVENT,
                invoice.getId(),
                invoice.getCustomerId(),
                invoice.getSubscriptionId(),
                invoice.getGrandTotal(),
                invoice.getDueDate()
        );
        outboxEventWriter.write(invoice.getId(), "Invoice", INVOICE_OVERDUE_EVENT, event);
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