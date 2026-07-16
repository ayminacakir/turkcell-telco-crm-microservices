package com.turkcell.notification_service.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Dokuman bolum 12: liste endpoint'leri ?page=0&size=20&sort=... ile sayfalanir.
 * Spring'in Page/PageImpl'i dogrudan serialize edilirse JSON formati surumler arasi
 * degisebildigi icin bu sabit zarf kullanilir (product-catalog'daki ile ayni sekil).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
