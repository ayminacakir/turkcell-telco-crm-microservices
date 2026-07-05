package com.turkcell.ticket_service.repository;

import com.turkcell.ticket_service.domain.entity.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {

    List<TicketComment> findByTicket_IdOrderByCreatedAtAsc(UUID ticketId);
}
