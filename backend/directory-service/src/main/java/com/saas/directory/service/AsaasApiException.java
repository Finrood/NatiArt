package com.saas.directory.service;

import org.springframework.http.HttpStatus;

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
