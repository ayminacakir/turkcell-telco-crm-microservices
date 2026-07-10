package com.turkcell.order_service.client;

import com.turkcell.order_service.client.dto.ProductResponse;
import com.turkcell.order_service.client.fallback.ProductClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-catalog-service", url = "http://localhost:9003", fallbackFactory = ProductClientFallback.class)
public interface ProductClient {

    @GetMapping("/api/v1/tariffs/{code}")
    ProductResponse getTariffByCode(@PathVariable("code") String code);

    @GetMapping("/api/v1/addons/{code}")
    ProductResponse getAddonByCode(@PathVariable("code") String code);
}
