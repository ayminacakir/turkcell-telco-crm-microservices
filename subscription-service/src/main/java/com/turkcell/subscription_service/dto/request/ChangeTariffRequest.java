package com.turkcell.subscription_service.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Abonenin tarifesini değiştirir (paket yükseltme/düşürme). */
public record ChangeTariffRequest(@NotBlank String tariffCode) {}
