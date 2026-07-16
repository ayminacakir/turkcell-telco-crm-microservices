package com.turkcell.ticket_service.repository;

import com.turkcell.ticket_service.domain.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByCustomerId(UUID customerId);

    org.springframework.data.domain.Page<Ticket> findByCustomerId(UUID customerId, org.springframework.data.domain.Pageable pageable);

    List<Ticket> findBySlaDueAtBeforeAndStatusNotInAndSlaBreachNotifiedFalse(
            java.time.LocalDateTime now, java.util.List<com.turkcell.ticket_service.domain.enums.TicketStatus> excludedStatuses);
}
