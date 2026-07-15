package com.turkcell.ticket_service.controller;

import com.turkcell.ticket_service.dto.*;
import com.turkcell.ticket_service.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketCreateRequest request) {
        TicketResponse created = ticketService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<TicketResponse>> getByCustomerId(@RequestParam UUID customerId) {
        return ResponseEntity.ok(ticketService.getByCustomerId(customerId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketResponse> updateStatus(@PathVariable UUID id,
                                                         @Valid @RequestBody TicketStatusUpdateRequest request) {
        return ResponseEntity.ok(ticketService.updateStatus(id, request));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<TicketResponse> assign(@PathVariable UUID id,
                                                  @Valid @RequestBody TicketAssignRequest request) {
        return ResponseEntity.ok(ticketService.assign(id, request));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<TicketResponse> resolve(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.resolve(id));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<TicketCommentResponse> addComment(@PathVariable UUID id,
                                                              @Valid @RequestBody TicketCommentCreateRequest request) {
        TicketCommentResponse created = ticketService.addComment(id, request);
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + id + "/comments/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<TicketCommentResponse>> getComments(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.getComments(id));
    }
}
