package com.turkcell.billing_service.service;
 
import com.turkcell.billing_service.domain.entity.Invoice;
import com.turkcell.billing_service.repository.BillCycleRepository;
import com.turkcell.billing_service.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
 
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
 
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingRunService {
 
    private final BillCycleRepository billCycleRepository;
    private final InvoiceRepository invoiceRepository;
 
    // Her gün gece yarısı otomatik çalışır
    @Scheduled(cron = "0 0 0 * * *")
    public void runBilling() {
        LocalDate today = LocalDate.now();
        log.info("Billing run started for date: {}", today);
 
        billCycleRepository.findAll().stream()
            // Test için filter'ı yorum satırına aldık,
            // production'da aşağıdaki satırı açık bırak:
            // .filter(cycle -> cycle.getNextRunDate().isEqual(today))
            .forEach(cycle -> {
                // Aynı dönem için fatura var mı kontrol et
                boolean alreadyInvoiced = invoiceRepository
                    .findByCustomerId(cycle.getCustomerId())
                    .stream()
                    .anyMatch(inv -> inv.getPeriodStart().equals(today.minusMonths(1))
                                 && inv.getPeriodEnd().equals(today));
 
                if (alreadyInvoiced) {
                    log.warn("Invoice already exists for customer: {}", cycle.getCustomerId());
                    return;
                }
 
                Invoice invoice = new Invoice();
                invoice.setCustomerId(cycle.getCustomerId());
                invoice.setSubscriptionId(cycle.getCustomerId()); // Kafka ile subscription gelince güncellenecek
                invoice.setPeriodStart(today.minusMonths(1));
                invoice.setPeriodEnd(today);
                invoice.setSubTotal(BigDecimal.ZERO);
                invoice.setTax(BigDecimal.ZERO);
                invoice.setGrandTotal(BigDecimal.ZERO);
                invoice.setStatus("DRAFT");
                invoice.setDueDate(today.plusDays(15));
                invoice.setIssuedAt(LocalDateTime.now());
                invoiceRepository.save(invoice);
 
                // Bir sonraki fatura tarihini güncelle
                cycle.setNextRunDate(today.plusMonths(1));
                billCycleRepository.save(cycle);
 
                log.info("Invoice created for customer: {}", cycle.getCustomerId());
            });
 
        log.info("Billing run completed for date: {}", today);
    }
}
 