package com.turkcell.order_service.client;

import com.turkcell.order_service.client.dto.CustomerResponse;
import com.turkcell.order_service.client.fallback.CustomerClientFallback;
import com.turkcell.order_service.config.FeignClientConfig;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "customer-service",
        url = "http://localhost:9002",
        fallbackFactory = CustomerClientFallback.class,
        configuration = FeignClientConfig.class)
public interface CustomerClient {

    @GetMapping("/api/v1/customers/{id}")
    CustomerResponse getById(@PathVariable("id") UUID id);
}
