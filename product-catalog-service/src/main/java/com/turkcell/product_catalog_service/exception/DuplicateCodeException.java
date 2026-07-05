package com.turkcell.product_catalog_service.exception;

public class DuplicateCodeException extends RuntimeException {

    public DuplicateCodeException(String message) {
        super(message);
    }
}
