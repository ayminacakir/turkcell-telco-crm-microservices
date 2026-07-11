package com.turkcell.billing_service.service;

import com.turkcell.billing_service.domain.entity.BillCycle;
import com.turkcell.billing_service.domain.entity.Invoice;
import com.turkcell.billing_service.event.InvoiceGeneratedEvent;
import com.turkcell.billing_service.outbox.service.OutboxEventWriter;
import com.turkcell.billing_service.repository.BillCycleRepository;
import com.turkcell.billing_service.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingRunService {

    private static final String INVOICE_GENERATED_EVENT = "InvoiceGenerated";
    private static final int DUE_DATE_OFFSET_DAYS = 15;

    private final BillCycleRepository billCycleRepository;
    private final InvoiceRepository invoiceRepository;
    private final OutboxEventWriter outboxEventWriter;

    // Her gun gece yarisi calisir; vadesi gelen bill cycle'lar icin fatura keser.
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void runBilling() {
        LocalDate today = LocalDate.now();
        log.info("Billing run started for date: {}", today);

        billCycleRepository.findAll().stream()
            .filter(cycle -> !cycle.getNextRunDate().isAfter(today))
            .forEach(cycle -> generateInvoiceForCycle(cycle, today));

        log.info("Billing run completed for date: {}", today);
    }

    private void generateInvoiceForCycle(BillCycle cycle, LocalDate today) {
        boolean alreadyInvoiced = invoiceRepository
            .findByCustomerId(cycle.getCustomerId())
            .stream()
            .anyMatch(inv -> inv.getPeriodStart().equals(today.minusMonths(1))
                         && inv.getPeriodEnd().equals(today));

        if (alreadyInvoiced) {
            log.warn("Invoice already exists for customer: {}", cycle.getCustomerId());
            return;
        }

        // Not: usage aggregation henuz baglanmadigi icin tutarlar sifir olarak kesilir.
        Invoice invoice = new Invoice();
        invoice.setCustomerId(cycle.getCustomerId());
        invoice.setSubscriptionId(cycle.getSubscriptionId() != null
                ? cycle.getSubscriptionId()
                : cycle.getCustomerId());
        invoice.setPeriodStart(today.minusMonths(1));
        invoice.setPeriodEnd(today);
        invoice.setSubTotal(BigDecimal.ZERO);
        invoice.setTax(BigDecimal.ZERO);
        invoice.setGrandTotal(BigDecimal.ZERO);
        invoice.setStatus("DRAFT");
        invoice.setDueDate(today.plusDays(DUE_DATE_OFFSET_DAYS));
        invoice.setIssuedAt(LocalDateTime.now());
        Invoice saved = invoiceRepository.save(invoice);

        cycle.setNextRunDate(today.plusMonths(1));
        billCycleRepository.save(cycle);

        InvoiceGeneratedEvent event = new InvoiceGeneratedEvent(
                UUID.randomUUID(),
                INVOICE_GENERATED_EVENT,
                saved.getId(),
                saved.getCustomerId(),
                saved.getSubscriptionId(),
                saved.getPeriodStart(),
                saved.getPeriodEnd(),
                saved.getGrandTotal(),
                saved.getDueDate(),
                saved.getIssuedAt()
        );
        outboxEventWriter.write(saved.getId(), "Invoice", INVOICE_GENERATED_EVENT, event);

        log.info("Invoice {} created for customer: {}", saved.getId(), cycle.getCustomerId());
    }
}
