package com.portcelana.natiart.service;

import org.springframework.http.HttpStatus;

/**
 * An upstream payment-provider response that is unusable (missing or malformed
 * fields). Messages stay static: the advice reflects them to the caller, so
 * they must never embed raw upstream text.
 */
public class AsaasApiException extends RuntimeException {
    private final HttpStatus httpStatus;

    public AsaasApiException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
