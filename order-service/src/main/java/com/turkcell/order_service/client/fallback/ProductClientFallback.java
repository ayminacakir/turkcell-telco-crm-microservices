package com.turkcell.order_service.client.fallback;

import com.turkcell.order_service.client.ProductClient;
import com.turkcell.order_service.client.dto.ProductResponse;
import com.turkcell.order_service.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class ProductClientFallback implements FallbackFactory<ProductClient> {

    private static final Logger log = LoggerFactory.getLogger(ProductClientFallback.class);

    @Override
    public ProductClient create(Throwable cause) {
        return new ProductClient() {
            @Override
            public ProductResponse getTariffByCode(String code) {
                log.error("product-catalog-service unavailable for tariffCode={}: {}", code, cause.getMessage());
                throw new ServiceUnavailableException("product-catalog-service is currently unavailable", cause);
            }

            @Override
            public ProductResponse getAddonByCode(String code) {
                log.error("product-catalog-service unavailable for addonCode={}: {}", code, cause.getMessage());
                throw new ServiceUnavailableException("product-catalog-service is currently unavailable", cause);
            }
        };
    }
}
