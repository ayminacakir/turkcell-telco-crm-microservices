package com.turkcell.billing_service.service;

import com.turkcell.billing_service.config.BillingProperties;
import com.turkcell.billing_service.domain.entity.BillCycle;
import com.turkcell.billing_service.domain.entity.Invoice;
import com.turkcell.billing_service.domain.entity.InvoiceLine;
import com.turkcell.billing_service.domain.entity.UsageAggregation;
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
import java.math.RoundingMode;
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
    private final UsageAggregationService usageAggregationService;
    private final OutboxEventWriter outboxEventWriter;
    private final BillingProperties billingProperties;

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
        LocalDate periodStart = today.minusMonths(1);
        LocalDate periodEnd = today;

        boolean alreadyInvoiced = invoiceRepository
            .findByCustomerId(cycle.getCustomerId())
            .stream()
            .anyMatch(inv -> inv.getPeriodStart().equals(periodStart)
                         && inv.getPeriodEnd().equals(periodEnd));

        if (alreadyInvoiced) {
            log.warn("Invoice already exists for customer: {}", cycle.getCustomerId());
            return;
        }

        UUID subscriptionId = cycle.getSubscriptionId() != null
                ? cycle.getSubscriptionId()
                : cycle.getCustomerId();

        UsageAggregation aggregation = usageAggregationService.findForPeriod(
                subscriptionId, periodStart, periodEnd);

        BigDecimal monthlyFee = cycle.getMonthlyFee() != null
                ? cycle.getMonthlyFee()
                : billingProperties.resolveMonthlyFee(cycle.getTariffCode());

        Invoice invoice = new Invoice();
        invoice.setCustomerId(cycle.getCustomerId());
        invoice.setSubscriptionId(subscriptionId);
        invoice.setPeriodStart(periodStart);
        invoice.setPeriodEnd(periodEnd);
        invoice.setStatus("ISSUED");
        invoice.setDueDate(today.plusDays(DUE_DATE_OFFSET_DAYS));
        invoice.setIssuedAt(LocalDateTime.now());

        BigDecimal subTotal = BigDecimal.ZERO;
        subTotal = subTotal.add(addLine(invoice, "Aylik tarife ucreti", BigDecimal.ONE, monthlyFee));

        if (aggregation != null) {
            BillingProperties.Overage rates = billingProperties.getOverage();
            if (aggregation.getOverageVoiceMinutes() > 0) {
                BigDecimal qty = BigDecimal.valueOf(aggregation.getOverageVoiceMinutes());
                subTotal = subTotal.add(addLine(invoice, "Dakika asimi",
                        qty, rates.getVoicePerMinute()));
            }
            if (aggregation.getOverageSms() > 0) {
                BigDecimal qty = BigDecimal.valueOf(aggregation.getOverageSms());
                subTotal = subTotal.add(addLine(invoice, "SMS asimi",
                        qty, rates.getSmsPerUnit()));
            }
            if (aggregation.getOverageDataMb() > 0) {
                BigDecimal qty = BigDecimal.valueOf(aggregation.getOverageDataMb());
                subTotal = subTotal.add(addLine(invoice, "Data asimi (MB)",
                        qty, rates.getDataPerMb()));
            }
        }

        BigDecimal tax = subTotal.multiply(billingProperties.getTaxRate())
                .setScale(2, RoundingMode.HALF_UP);
        invoice.setSubTotal(subTotal);
        invoice.setTax(tax);
        invoice.setGrandTotal(subTotal.add(tax));

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

        log.info("Invoice {} created for customer: {}, grandTotal={}",
                saved.getId(), cycle.getCustomerId(), saved.getGrandTotal());
    }

    private BigDecimal addLine(Invoice invoice, String description, BigDecimal quantity, BigDecimal unitPrice) {
        InvoiceLine line = new InvoiceLine();
        line.setInvoice(invoice);
        line.setDescription(description);
        line.setQuantity(quantity);
        line.setUnitPrice(unitPrice);
        line.setLineTotal(quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
        invoice.getLines().add(line);
        return line.getLineTotal();
    }
}
