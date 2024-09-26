package com.portcelana.natiart.controller.helper;

import org.springframework.http.HttpStatus;

public class ResourceAlreadyExistsException extends RuntimeException {
    private final HttpStatus httpStatus;

    public ResourceAlreadyExistsException(String message) {
        super(message);
        this.httpStatus = HttpStatus.CONFLICT;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
