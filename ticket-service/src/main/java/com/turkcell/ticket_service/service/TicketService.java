package com.turkcell.ticket_service.service;

import com.turkcell.ticket_service.dto.*;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TicketService {

    TicketResponse create(TicketCreateRequest request);

    TicketResponse getById(UUID id);

    PageResponse<TicketResponse> getByCustomerId(UUID customerId, Pageable pageable);

    // Operasyon konsolu icin: tum musterilerin biletleri (admin).
    PageResponse<TicketResponse> getAll(Pageable pageable);

    TicketResponse updateStatus(UUID id, TicketStatusUpdateRequest request);

    TicketCommentResponse addComment(UUID ticketId, TicketCommentCreateRequest request);

    List<TicketCommentResponse> getComments(UUID ticketId);

    TicketResponse assign(UUID id, TicketAssignRequest request);

    TicketResponse resolve(UUID id);
}
