package com.turkcell.ticket_service.service;

import com.turkcell.ticket_service.domain.entity.Ticket;
import com.turkcell.ticket_service.domain.enums.TicketPriority;
import com.turkcell.ticket_service.domain.enums.TicketStatus;
import com.turkcell.ticket_service.dto.TicketCreateRequest;
import com.turkcell.ticket_service.dto.TicketResponse;
import com.turkcell.ticket_service.dto.TicketStatusUpdateRequest;
import com.turkcell.ticket_service.exception.InvalidStatusTransitionException;
import com.turkcell.ticket_service.exception.ResourceNotFoundException;
import com.turkcell.ticket_service.repository.TicketCommentRepository;
import com.turkcell.ticket_service.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.ticket_service.repository.TicketRepository;
import com.turkcell.ticket_service.service.impl.TicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketCommentRepository ticketCommentRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private UUID ticketId;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
    }

    private Ticket ticketWithStatus(TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setCustomerId(UUID.randomUUID());
        ticket.setCategory("BILLING");
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setStatus(status);
        ticket.setCreatedAt(LocalDateTime.now());
        return ticket;
    }

    @ParameterizedTest(name = "{0} -> {1} izinli olmalı")
    @CsvSource({
            "OPEN, IN_PROGRESS",
            "OPEN, CLOSED",
            "IN_PROGRESS, RESOLVED",
            "IN_PROGRESS, CLOSED",
            "RESOLVED, IN_PROGRESS",
            "RESOLVED, CLOSED"
    })
    void updateStatus_shouldSucceed_forAllowedTransitions(TicketStatus from, TicketStatus to) {
        Ticket ticket = ticketWithStatus(from);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        TicketResponse response = ticketService.updateStatus(ticketId, new TicketStatusUpdateRequest(to));

        assertThat(response.status()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1} REDDEDILMELI")
    @CsvSource({
            "OPEN, RESOLVED",       // arada IN_PROGRESS atlanamaz
            "CLOSED, OPEN",         // kapalı bilet yeniden açılamaz
            "CLOSED, IN_PROGRESS",
            "RESOLVED, OPEN"
    })
    void updateStatus_shouldThrow_forDisallowedTransitions(TicketStatus from, TicketStatus to) {
        Ticket ticket = ticketWithStatus(from);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.updateStatus(ticketId, new TicketStatusUpdateRequest(to)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void updateStatus_shouldBeNoOp_whenTargetEqualsCurrent() {
        Ticket ticket = ticketWithStatus(TicketStatus.OPEN);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        TicketResponse response = ticketService.updateStatus(ticketId, new TicketStatusUpdateRequest(TicketStatus.OPEN));

        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void updateStatus_shouldThrowResourceNotFound_whenTicketMissing() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                ticketService.updateStatus(ticketId, new TicketStatusUpdateRequest(TicketStatus.IN_PROGRESS)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest(name = "{0} oncelik icin SLA {1} saat olmali")
    @CsvSource({
            "CRITICAL, 4",
            "HIGH, 8",
            "MEDIUM, 24",
            "LOW, 72"
    })
    void create_shouldSetSlaDueAt_basedOnPriority(TicketPriority priority, long expectedHours) {
        UUID customerId = UUID.randomUUID();
        TicketCreateRequest request = new TicketCreateRequest(
                customerId, "BILLING", priority, "Faturam yanlis geldi");

        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(ticketId);
            return t;
        });

        TicketResponse response = ticketService.create(request);

        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.slaDueAt()).isEqualTo(response.createdAt().plusHours(expectedHours));
        verify(ticketCommentRepository, atLeastOnce()).save(any());
    }
}
