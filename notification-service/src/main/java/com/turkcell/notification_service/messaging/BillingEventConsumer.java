package com.turkcell.notification_service.messaging;

import com.turkcell.notification_service.domain.entity.ProcessedEvent;
import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.event.InvoiceGeneratedEvent;
import com.turkcell.notification_service.repository.ProcessedEventRepository;
import com.turkcell.notification_service.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * invoice.generated topic'ini dinler, fatura kesildi bildirimi gonderir.
 * Senaryo 2 (Aylik Fatura), Adim 10.
 */
@Component
public class BillingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BillingEventConsumer.class);

    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    public BillingEventConsumer(NotificationService notificationService,
                                 ProcessedEventRepository processedEventRepository) {
        this.notificationService = notificationService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "invoice.generated", groupId = "notification-service",
            containerFactory = "invoiceGeneratedContainerFactory")
    @Transactional
    public void onInvoiceGenerated(InvoiceGeneratedEvent event) {
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("invoice.generated event zaten islenmis, atlaniyor: eventId={}", event.eventId());
            return;
        }

        log.info("invoice.generated alindi: invoiceId={}, customerId={}",
                event.invoiceId(), event.customerId());

        SendNotificationRequest request = new SendNotificationRequest(
                event.customerId(),
                "INVOICE_GENERATED",
                Map.of(
                        "amount", event.amount() == null ? "" : event.amount().toString(),
                        "currency", event.currency() == null ? "" : event.currency(),
                        "dueDate", event.dueDate() == null ? "" : event.dueDate().toString()
                )
        );
        notificationService.send(request);

        processedEventRepository.save(new ProcessedEvent(event.eventId(), "InvoiceGenerated"));
    }
}
