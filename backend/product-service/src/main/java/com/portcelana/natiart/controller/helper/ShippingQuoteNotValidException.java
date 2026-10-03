package com.portcelana.natiart.controller.helper;

import org.springframework.http.HttpStatus;

public class ShippingQuoteNotValidException extends RuntimeException {
    private final HttpStatus httpStatus = HttpStatus.CONFLICT;

    public ShippingQuoteNotValidException(String message) {
        super(message);
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
