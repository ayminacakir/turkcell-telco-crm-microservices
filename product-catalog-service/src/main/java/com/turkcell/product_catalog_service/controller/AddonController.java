package com.turkcell.product_catalog_service.controller;

import com.turkcell.product_catalog_service.dto.AddonCreateRequest;
import com.turkcell.product_catalog_service.dto.AddonResponse;
import com.turkcell.product_catalog_service.service.AddonService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/addons")
public class AddonController {

    private final AddonService addonService;

    public AddonController(AddonService addonService) {
        this.addonService = addonService;
    }

    @PostMapping
    public ResponseEntity<AddonResponse> create(@Valid @RequestBody AddonCreateRequest request) {
        AddonResponse created = addonService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/addons/" + created.code())).body(created);
    }

    @GetMapping("/{code}")
    public ResponseEntity<AddonResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(addonService.getByCode(code));
    }

    @GetMapping
    public ResponseEntity<List<AddonResponse>> getByTariffCode(
            @RequestParam(name = "tariffCode") String tariffCode) {
        return ResponseEntity.ok(addonService.getByTariffCode(tariffCode));
    }

    /**
     * Bir ek paketi bir tarifeye baglar. Idempotent: iliski zaten varsa 204 doner, hata firlatmaz.
     */
    @PutMapping("/{addonCode}/tariffs/{tariffCode}")
    public ResponseEntity<Void> linkToTariff(@PathVariable String addonCode, @PathVariable String tariffCode) {
        addonService.linkToTariff(tariffCode, addonCode);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{addonCode}/tariffs/{tariffCode}")
    public ResponseEntity<Void> unlinkFromTariff(@PathVariable String addonCode, @PathVariable String tariffCode) {
        addonService.unlinkFromTariff(tariffCode, addonCode);
        return ResponseEntity.noContent().build();
    }
}
