package com.turkcell.ticket_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.ticket_service.domain.entity.Ticket;
import com.turkcell.ticket_service.domain.entity.TicketComment;
import com.turkcell.ticket_service.domain.enums.TicketPriority;
import com.turkcell.ticket_service.domain.enums.TicketStatus;
import com.turkcell.ticket_service.dto.*;
import com.turkcell.ticket_service.exception.InvalidStatusTransitionException;
import com.turkcell.ticket_service.exception.ResourceNotFoundException;
import com.turkcell.ticket_service.outbox.entity.OutboxEvent;
import com.turkcell.ticket_service.outbox.enums.OutboxStatus;
import com.turkcell.ticket_service.outbox.event.SlaBreachedEvent;
import com.turkcell.ticket_service.outbox.event.TicketOpenedEvent;
import com.turkcell.ticket_service.outbox.event.TicketResolvedEvent;
import com.turkcell.ticket_service.outbox.repository.OutboxEventRepository;
import com.turkcell.ticket_service.repository.TicketCommentRepository;
import com.turkcell.ticket_service.repository.TicketRepository;
import com.turkcell.ticket_service.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TicketServiceImpl.class);

    /** Öncelik bazlı SLA süresi (saat). */
    private static final Map<TicketPriority, Long> SLA_HOURS = Map.of(
            TicketPriority.CRITICAL, 4L,
            TicketPriority.HIGH, 8L,
            TicketPriority.MEDIUM, 24L,
            TicketPriority.LOW, 72L
    );

    /**
     * FR-32: Ticket otomatik olarak ilgili ekibe SLA bazli atanir.
     * Oncelik ne kadar yuksekse, o kadar kidemli/hizli ekibe dusuyor.
     */
    private static final Map<TicketPriority, String> AUTO_ASSIGN_TEAM = Map.of(
            TicketPriority.CRITICAL, "Kidemli Destek Ekibi",
            TicketPriority.HIGH, "Kidemli Destek Ekibi",
            TicketPriority.MEDIUM, "Genel Destek Ekibi",
            TicketPriority.LOW, "Genel Destek Ekibi"
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
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public TicketServiceImpl(TicketRepository ticketRepository,
                              TicketCommentRepository ticketCommentRepository,
                              OutboxEventRepository outboxEventRepository,
                              ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
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
        ticket.setAssignedTeam(AUTO_ASSIGN_TEAM.get(request.priority()));

        Ticket saved = ticketRepository.save(ticket);

        // İlk açıklama, müşterinin kendisi tarafından yazılmış ilk yorum olarak tutulur.
        TicketComment initialComment = new TicketComment();
        initialComment.setTicket(saved);
        initialComment.setAuthorId(request.customerId());
        initialComment.setBody(request.description());
        initialComment.setCreatedAt(now);
        ticketCommentRepository.save(initialComment);

        saveOutboxEvent(saved.getId(), "TicketOpened",
                new TicketOpenedEvent(
                        UUID.randomUUID(), "TicketOpened",
                        saved.getId(), saved.getCustomerId(), saved.getCategory(),
                        saved.getPriority(), saved.getSlaDueAt(), now));

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
        transitionTo(ticket, request.status());
        return toResponse(ticket);
    }

    @Override
    public TicketResponse assign(UUID id, TicketAssignRequest request) {
        Ticket ticket = findEntity(id);
        ticket.setAssignedTeam(request.team());
        return toResponse(ticket);
    }

    @Override
    public TicketResponse resolve(UUID id) {
        Ticket ticket = findEntity(id);
        transitionTo(ticket, TicketStatus.RESOLVED);
        return toResponse(ticket);
    }

    /**
     * Durum gecisini dogrular, uygular ve RESOLVED'a geciste TicketResolved event'ini yayinlar.
     * updateStatus() (generic) ve resolve() (spec'teki dedicated endpoint) ayni mantigi kullanir.
     */
    private void transitionTo(Ticket ticket, TicketStatus target) {
        TicketStatus current = ticket.getStatus();

        if (current == target) {
            return;
        }

        if (!ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(TicketStatus.class)).contains(target)) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition ticket from " + current + " to " + target);
        }

        ticket.setStatus(target);

        if (target == TicketStatus.RESOLVED) {
            saveOutboxEvent(ticket.getId(), "TicketResolved",
                    new TicketResolvedEvent(
                            UUID.randomUUID(), "TicketResolved",
                            ticket.getId(), ticket.getCustomerId(), LocalDateTime.now()));
        }
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

    /**
     * SLA suresi gecmis ve henuz kapatilmamis/cozulmemis biletleri bulur,
     * her biri icin bir kez SlaBreached event'i yayinlar. OutboxSlaSchedulerService
     * tarafindan periyodik olarak cagrilir.
     */
    public void checkSlaBreaches() {
        LocalDateTime now = LocalDateTime.now();
        List<Ticket> breached = ticketRepository.findBySlaDueAtBeforeAndStatusNotInAndSlaBreachNotifiedFalse(
                now, List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED));

        for (Ticket ticket : breached) {
            ticket.setSlaBreachNotified(true);
            ticketRepository.save(ticket);

            saveOutboxEvent(ticket.getId(), "SlaBreached",
                    new SlaBreachedEvent(
                            UUID.randomUUID(), "SlaBreached",
                            ticket.getId(), ticket.getCustomerId(), ticket.getPriority(),
                            ticket.getSlaDueAt(), now));

            log.info("SLA breached for ticket {}", ticket.getId());
        }
    }

    private Ticket findEntity(UUID id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id));
    }

    private void saveOutboxEvent(UUID aggregateId, String eventType, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + eventType + " event", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setAggregateType("TICKET");
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(json);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEventRepository.save(outboxEvent);
    }

    private TicketResponse toResponse(Ticket t) {
        return new TicketResponse(
                t.getId(), t.getCustomerId(), t.getCategory(), t.getPriority(),
                t.getStatus(), t.getSlaDueAt(), t.getCreatedAt(), t.getAssignedTeam()
        );
    }

    private TicketCommentResponse toResponse(TicketComment c) {
        return new TicketCommentResponse(
                c.getId(), c.getTicket().getId(), c.getAuthorId(), c.getBody(), c.getCreatedAt()
        );
    }
}
