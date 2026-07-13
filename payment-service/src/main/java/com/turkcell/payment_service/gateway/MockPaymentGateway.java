package com.turkcell.payment_service.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Random;

@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final double successRate;
    private final Random random = new Random();

    public MockPaymentGateway(@Value("${payment.gateway.success-rate}") double successRate) {
        this.successRate = successRate;
    }

    @Override
    public boolean charge(String method, BigDecimal amount) {
        boolean success = random.nextDouble() < successRate;
        log.info("Mock payment gateway charge: method={}, amount={}, success={}", method, amount, success);
        return success;
    }
}
