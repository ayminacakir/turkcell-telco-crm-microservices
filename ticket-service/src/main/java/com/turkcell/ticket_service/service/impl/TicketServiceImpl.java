package com.turkcell.ticket_service.service.impl;

import com.turkcell.ticket_service.domain.entity.Ticket;
import com.turkcell.ticket_service.domain.entity.TicketComment;
import com.turkcell.ticket_service.domain.enums.TicketPriority;
import com.turkcell.ticket_service.domain.enums.TicketStatus;
import com.turkcell.ticket_service.dto.*;
import com.turkcell.ticket_service.exception.InvalidStatusTransitionException;
import com.turkcell.ticket_service.exception.ResourceNotFoundException;
import com.turkcell.ticket_service.repository.TicketCommentRepository;
import com.turkcell.ticket_service.repository.TicketRepository;
import com.turkcell.ticket_service.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class TicketServiceImpl implements TicketService {

    /** Öncelik bazlı SLA süresi (saat). */
    private static final Map<TicketPriority, Long> SLA_HOURS = Map.of(
            TicketPriority.CRITICAL, 4L,
            TicketPriority.HIGH, 8L,
            TicketPriority.MEDIUM, 24L,
            TicketPriority.LOW, 72L
    );

    /** İzin verilen durum geçişleri. RESOLVED -> IN_PROGRESS (reopen) dahil. */
    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(TicketStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(TicketStatus.OPEN, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(TicketStatus.IN_PROGRESS, EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(TicketStatus.RESOLVED, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(TicketStatus.CLOSED, EnumSet.noneOf(TicketStatus.class));
    }

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;

    public TicketServiceImpl(TicketRepository ticketRepository, TicketCommentRepository ticketCommentRepository) {
        this.ticketRepository = ticketRepository;
        this.ticketCommentRepository = ticketCommentRepository;
    }

    @Override
    public TicketResponse create(TicketCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();

        Ticket ticket = new Ticket();
        ticket.setCustomerId(request.customerId());
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setCreatedAt(now);
        ticket.setSlaDueAt(now.plusHours(SLA_HOURS.get(request.priority())));

        Ticket saved = ticketRepository.save(ticket);

        // İlk açıklama, müşterinin kendisi tarafından yazılmış ilk yorum olarak tutulur.
        TicketComment initialComment = new TicketComment();
        initialComment.setTicket(saved);
        initialComment.setAuthorId(request.customerId());
        initialComment.setBody(request.description());
        initialComment.setCreatedAt(now);
        ticketCommentRepository.save(initialComment);

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getById(UUID id) {
        return toResponse(findEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponse> getByCustomerId(UUID customerId) {
        return ticketRepository.findByCustomerId(customerId).stream().map(this::toResponse).toList();
    }

    @Override
    public TicketResponse updateStatus(UUID id, TicketStatusUpdateRequest request) {
        Ticket ticket = findEntity(id);
        TicketStatus current = ticket.getStatus();
        TicketStatus target = request.status();

        if (current == target) {
            return toResponse(ticket);
        }

        if (!ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(TicketStatus.class)).contains(target)) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition ticket from " + current + " to " + target);
        }

        ticket.setStatus(target);
        return toResponse(ticket);
    }

    @Override
    public TicketCommentResponse addComment(UUID ticketId, TicketCommentCreateRequest request) {
        Ticket ticket = findEntity(ticketId);

        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthorId(request.authorId());
        comment.setBody(request.body());
        comment.setCreatedAt(LocalDateTime.now());

        return toResponse(ticketCommentRepository.save(comment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketCommentResponse> getComments(UUID ticketId) {
        // Ticket'ın var olduğunu doğrula, yoksa 404 dönsün.
        findEntity(ticketId);
        return ticketCommentRepository.findByTicket_IdOrderByCreatedAtAsc(ticketId).stream()
                .map(this::toResponse)
                .toList();
    }

    private Ticket findEntity(UUID id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id));
    }

    private TicketResponse toResponse(Ticket t) {
        return new TicketResponse(
                t.getId(), t.getCustomerId(), t.getCategory(), t.getPriority(),
                t.getStatus(), t.getSlaDueAt(), t.getCreatedAt()
        );
    }

    private TicketCommentResponse toResponse(TicketComment c) {
        return new TicketCommentResponse(
                c.getId(), c.getTicket().getId(), c.getAuthorId(), c.getBody(), c.getCreatedAt()
        );
    }
}
