package com.turkcell.billing_service.kafka;

import com.turkcell.billing_service.event.PaymentCompletedEvent;
import com.turkcell.billing_service.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final InvoiceRepository invoiceRepository;

    @KafkaListener(
        topics = "payment.completed",
        groupId = "billing-service-group"
    )
    @KafkaListener(topics = "payment.completed", groupId = "billing-service-group")
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    log.info("PaymentCompleted event received for invoiceId: {}", event.getInvoiceId());

    invoiceRepository.findById(event.getInvoiceId()).ifPresentOrElse(
        invoice -> {
            if ("COMPLETED".equals(event.getStatus())) {
                invoice.setStatus("PAID");
                invoiceRepository.save(invoice);
                log.info("Invoice {} marked as PAID", event.getInvoiceId());
            } else if ("FAILED".equals(event.getStatus())) {
                invoice.setStatus("OVERDUE");
                invoiceRepository.save(invoice);
                log.info("Invoice {} marked as OVERDUE", event.getInvoiceId());
            }
        },
        () -> log.warn("Invoice not found: {}", event.getInvoiceId())
    );
}
}