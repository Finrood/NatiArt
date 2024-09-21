package com.portcelana.natiart.controller.helper;

import org.springframework.http.HttpStatus;

public class UserNotAllowedException extends  RuntimeException {
    private final HttpStatus httpStatus;

    public UserNotAllowedException(String message) {
        super(message);
        this.httpStatus = HttpStatus.FORBIDDEN;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
