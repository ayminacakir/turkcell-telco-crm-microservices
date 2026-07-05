package com.turkcell.ticket_service.service;

import com.turkcell.ticket_service.dto.*;

import java.util.List;
import java.util.UUID;

public interface TicketService {

    TicketResponse create(TicketCreateRequest request);

    TicketResponse getById(UUID id);

    List<TicketResponse> getByCustomerId(UUID customerId);

    TicketResponse updateStatus(UUID id, TicketStatusUpdateRequest request);

    TicketCommentResponse addComment(UUID ticketId, TicketCommentCreateRequest request);

    List<TicketCommentResponse> getComments(UUID ticketId);
}
