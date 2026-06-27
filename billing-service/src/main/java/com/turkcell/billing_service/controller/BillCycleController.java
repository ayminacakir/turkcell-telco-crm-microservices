package com.turkcell.billing_service.controller;

import com.turkcell.billing_service.dto.request.CreateBillCycleRequest;
import com.turkcell.billing_service.dto.response.BillCycleResponse;
import com.turkcell.billing_service.service.BillCycleService;
import com.turkcell.billing_service.service.BillingRunService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bill-cycles")
@RequiredArgsConstructor
public class BillCycleController {

    private final BillCycleService billCycleService;
    private final BillingRunService billingRunService;

    @PostMapping
    public ResponseEntity<BillCycleResponse> create(@Valid @RequestBody CreateBillCycleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billCycleService.create(request));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<BillCycleResponse>> getByCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok(billCycleService.getByCustomerId(customerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BillCycleResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(billCycleService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        billCycleService.delete(id);
        return ResponseEntity.noContent().build();
    }
     // Admin manuel tetikleme
    @PostMapping("/runs")
    public ResponseEntity<String> triggerBillingRun() {
        billingRunService.runBilling();
        return ResponseEntity.ok("Billing run started");
    }
}