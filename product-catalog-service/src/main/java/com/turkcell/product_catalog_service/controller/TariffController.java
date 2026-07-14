package com.turkcell.product_catalog_service.controller;

import com.turkcell.product_catalog_service.dto.TariffCreateRequest;
import com.turkcell.product_catalog_service.dto.TariffResponse;
import com.turkcell.product_catalog_service.service.TariffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tariffs")
public class TariffController {

    private final TariffService tariffService;

    public TariffController(TariffService tariffService) {
        this.tariffService = tariffService;
    }

    @PostMapping
    public ResponseEntity<TariffResponse> create(@Valid @RequestBody TariffCreateRequest request) {
        TariffResponse created = tariffService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/tariffs/" + created.code())).body(created);
    }

    @GetMapping("/{code}")
    public ResponseEntity<TariffResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(tariffService.getByCode(code));
    }

    @PatchMapping("/{code}/price")
    public ResponseEntity<TariffResponse> updatePrice(
            @PathVariable String code,
            @RequestBody java.math.BigDecimal newMonthlyFee) {
        return ResponseEntity.ok(tariffService.updatePrice(code, newMonthlyFee));
    }

    @GetMapping
    public ResponseEntity<List<TariffResponse>> getAll(
            @RequestParam(name = "status", required = false) String status) {
        if ("ACTIVE".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(tariffService.getActive());
        }
        return ResponseEntity.ok(tariffService.getAll());
    }
}
