package com.turkcell.subscription_service.controller;

import com.turkcell.subscription_service.dto.request.ActivateSubscriptionRequest;
import com.turkcell.subscription_service.dto.request.CreateMsisdnPoolRequest;
import com.turkcell.subscription_service.dto.request.CreateSimCardRequest;
import com.turkcell.subscription_service.dto.request.UpdateMnpStatusRequest;
import com.turkcell.subscription_service.dto.response.MsisdnPoolResponse;
import com.turkcell.subscription_service.dto.response.SimCardResponse;
import com.turkcell.subscription_service.dto.response.SubscriptionResponse;
import com.turkcell.subscription_service.service.SubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/msisdns")
    @ResponseStatus(HttpStatus.CREATED)
    public MsisdnPoolResponse createMsisdn(@Valid @RequestBody CreateMsisdnPoolRequest request) {
        return subscriptionService.createMsisdn(request);
    }

    @PostMapping("/sim-cards")
    @ResponseStatus(HttpStatus.CREATED)
    public SimCardResponse createSimCard(@Valid @RequestBody CreateSimCardRequest request) {
        return subscriptionService.createSimCard(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse activate(@Valid @RequestBody ActivateSubscriptionRequest request) {
        return subscriptionService.activate(request);
    }

    @GetMapping("/{id}")
    public SubscriptionResponse getById(@PathVariable UUID id) {
        return subscriptionService.getById(id);
    }

    @GetMapping("/customers/{customerId}")
    public List<SubscriptionResponse> getByCustomerId(@PathVariable UUID customerId) {
        return subscriptionService.getByCustomerId(customerId);
    }

    @GetMapping("/active")
    public List<SubscriptionResponse> getActiveSubscriptions() {
        return subscriptionService.getActiveSubscriptions();
    }

    @PostMapping("/{id}/suspend")
    public SubscriptionResponse suspend(@PathVariable UUID id) {
        return subscriptionService.suspend(id);
    }

    @PostMapping("/{id}/reactivate")
    public SubscriptionResponse reactivate(@PathVariable UUID id) {
        return subscriptionService.reactivate(id);
    }

    @PostMapping("/{id}/terminate")
    public SubscriptionResponse terminate(@PathVariable UUID id) {
        return subscriptionService.terminate(id);
    }

    @PatchMapping("/{id}/mnp-status")
    public SubscriptionResponse updateMnpStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMnpStatusRequest request) {
        return subscriptionService.updateMnpStatus(id, request);
    }
}
