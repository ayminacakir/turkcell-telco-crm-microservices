package com.turkcell.notification_service.messaging;

import com.turkcell.notification_service.domain.entity.ProcessedEvent;
import com.turkcell.notification_service.dto.SendNotificationRequest;
import com.turkcell.notification_service.event.TicketOpenedEvent;
import com.turkcell.notification_service.event.TicketResolvedEvent;
import com.turkcell.notification_service.repository.ProcessedEventRepository;
import com.turkcell.notification_service.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * ticket.opened ve ticket.resolved topic'lerini dinler, musteriye bildirim gonderir.
 * FR-33: "Ticket durumu acildiginda musteriye bildirim gider."
 */
@Component
public class TicketEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketEventConsumer.class);

    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    public TicketEventConsumer(NotificationService notificationService,
                                ProcessedEventRepository processedEventRepository) {
        this.notificationService = notificationService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "ticket.opened", groupId = "notification-service",
            containerFactory = "ticketOpenedContainerFactory")
    @Transactional
    public void onTicketOpened(TicketOpenedEvent event) {
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("ticket.opened event zaten islenmis, atlaniyor: eventId={}", event.eventId());
            return;
        }

        log.info("ticket.opened alindi: ticketId={}, customerId={}", event.ticketId(), event.customerId());

        SendNotificationRequest request = new SendNotificationRequest(
                event.customerId(),
                "TICKET_OPENED",
                Map.of(
                        "category", event.category() == null ? "" : event.category(),
                        "priority", event.priority() == null ? "" : event.priority()
                )
        );
        notificationService.send(request);

        processedEventRepository.save(new ProcessedEvent(event.eventId(), "TicketOpened"));
    }

    @KafkaListener(topics = "ticket.resolved", groupId = "notification-service",
            containerFactory = "ticketResolvedContainerFactory")
    @Transactional
    public void onTicketResolved(TicketResolvedEvent event) {
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("ticket.resolved event zaten islenmis, atlaniyor: eventId={}", event.eventId());
            return;
        }

        log.info("ticket.resolved alindi: ticketId={}, customerId={}", event.ticketId(), event.customerId());

        SendNotificationRequest request = new SendNotificationRequest(
                event.customerId(),
                "TICKET_RESOLVED",
                Map.of()
        );
        notificationService.send(request);

        processedEventRepository.save(new ProcessedEvent(event.eventId(), "TicketResolved"));
    }
}
