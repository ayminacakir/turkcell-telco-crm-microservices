package com.turkcell.ticket_service.scheduler;

import com.turkcell.ticket_service.service.impl.TicketServiceImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlaBreachScheduler {

    private final TicketServiceImpl ticketService;

    public SlaBreachScheduler(TicketServiceImpl ticketService) {
        this.ticketService = ticketService;
    }

    @Scheduled(fixedDelay = 60000)
    public void checkSlaBreaches() {
        ticketService.checkSlaBreaches();
    }
}
