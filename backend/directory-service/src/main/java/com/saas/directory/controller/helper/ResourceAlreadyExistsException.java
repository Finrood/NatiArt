package com.saas.directory.controller.helper;

import org.springframework.http.HttpStatus;

public class ResourceAlreadyExistsException extends RuntimeException {
    private final HttpStatus httpStatus;

    public ResourceAlreadyExistsException(String message) {
        super(message);
        this.httpStatus = HttpStatus.CONFLICT;
    }

    public ResourceAlreadyExistsException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
