package com.turkcell.product_catalog_service.controller;

import com.turkcell.product_catalog_service.dto.PageResponse;
import com.turkcell.product_catalog_service.dto.TariffCreateRequest;
import com.turkcell.product_catalog_service.dto.TariffResponse;
import com.turkcell.product_catalog_service.service.TariffService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    /** Dokuman 8.2: POST /tariffs (admin). */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TariffResponse> create(@Valid @RequestBody TariffCreateRequest request) {
        TariffResponse created = tariffService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/tariffs/" + created.code())).body(created);
    }

    @GetMapping("/{code}")
    public ResponseEntity<TariffResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(tariffService.getByCode(code));
    }

    /** FR-08: Tarifenin arsivlenmis eski versiyonlari (en yeniden eskiye). */
    @GetMapping("/{code}/versions")
    public ResponseEntity<List<TariffResponse>> getVersions(@PathVariable String code) {
        return ResponseEntity.ok(tariffService.getVersions(code));
    }

    @PatchMapping("/{code}/price")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TariffResponse> updatePrice(
            @PathVariable String code,
            @RequestBody java.math.BigDecimal newMonthlyFee) {
        return ResponseEntity.ok(tariffService.updatePrice(code, newMonthlyFee));
    }

    /** Dokuman bolum 12: ?page=0&size=20&sort=code,asc — Spring Data Pageable. */
    @GetMapping
    public ResponseEntity<PageResponse<TariffResponse>> getAll(
            @RequestParam(name = "status", required = false) String status,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        if ("ACTIVE".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(tariffService.getActive(pageable));
        }
        return ResponseEntity.ok(tariffService.getAll(pageable));
    }
}
