package com.turkcell.order_service.client.fallback;

import com.turkcell.order_service.client.CustomerClient;
import com.turkcell.order_service.client.dto.CustomerResponse;
import com.turkcell.order_service.exception.ServiceUnavailableException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class CustomerClientFallback implements FallbackFactory<CustomerClient> {

    private static final Logger log = LoggerFactory.getLogger(CustomerClientFallback.class);

    @Override
    public CustomerClient create(Throwable cause) {
        return new CustomerClient() {
            @Override
            public CustomerResponse getById(UUID id) {
                log.error("customer-service unavailable for customerId={}: {}", id, cause.getMessage());
                throw new ServiceUnavailableException("customer-service is currently unavailable", cause);
            }
        };
    }
}
