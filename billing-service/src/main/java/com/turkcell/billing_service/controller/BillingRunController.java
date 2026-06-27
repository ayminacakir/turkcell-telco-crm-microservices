package com.turkcell.billing_service.controller;
 
import com.turkcell.billing_service.service.BillingRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
 
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingRunController {
 
    private final BillingRunService billingRunService;
 
    @PostMapping("/runs")
    public ResponseEntity<String> triggerBillingRun() {
        billingRunService.runBilling();
        return ResponseEntity.ok("Billing run started successfully");
    }
}
 