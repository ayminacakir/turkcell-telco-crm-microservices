package com.turkcell.order_service.exception;

public class CustomerNotActiveException extends RuntimeException {

    public CustomerNotActiveException(String message) {
        super(message);
    }
}
