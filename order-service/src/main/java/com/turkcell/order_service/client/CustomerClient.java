package com.turkcell.order_service.client;

import com.turkcell.order_service.client.dto.CustomerResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "customer-service", url = "http://localhost:9002")
public interface CustomerClient {

    @GetMapping("/api/v1/customers/{id}")
    CustomerResponse getById(@PathVariable("id") UUID id);
}
