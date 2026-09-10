package com.portcelana.natiart.service;

import org.springframework.http.HttpStatus;

/**
 * A safe, client-facing failure returned by an external service.
 *
 * <p>The message is deliberately supplied by the local service rather than
 * copied from the upstream response body. The optional {@code Retry-After}
 * value is retained separately so the controller advice can forward it without
 * exposing provider response details.
 */
public class UpstreamServiceException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String retryAfter;

    public UpstreamServiceException(String message, HttpStatus httpStatus) {
        this(message, httpStatus, null);
    }

    public UpstreamServiceException(String message, HttpStatus httpStatus, String retryAfter) {
        super(message);
        this.httpStatus = httpStatus;
        this.retryAfter = retryAfter;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getRetryAfter() {
        return retryAfter;
    }
}
