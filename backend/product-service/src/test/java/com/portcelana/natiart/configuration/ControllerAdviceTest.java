package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;

class ControllerAdviceTest {

    private ControllerAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new ControllerAdvice();
    }

    @Test
    void handleException_returnsStaticMessageAnd500() {
        final ResponseEntity<Object> result =
                advice.handleException(new RuntimeException("select * from secret_table"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertEquals("Internal server error", result.getBody());
    }

    @Test
    void handleAccessDeniedException_returns403WithoutRethrow() {
        final ResponseEntity<Object> result = advice.handleAccessDeniedException(new AccessDeniedException("denied"));

        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
        assertEquals("Access denied", result.getBody());
    }

    @Test
    void handleNotReadableBody_unwrapsGuardFailureTo400WithItsMessage() {
        final HttpMessageNotReadableException unreadable = new HttpMessageNotReadableException(
                "JSON parse error", new IllegalArgumentException("Billing type is required"));

        final ResponseEntity<Object> result = advice.handleNotReadableBody(unreadable);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertEquals("Billing type is required", result.getBody());
    }

    @Test
    void handleNotReadableBody_mapsUnrelatedParseErrorsToGeneric400() {
        final HttpMessageNotReadableException unreadable = new HttpMessageNotReadableException("JSON parse error");

        final ResponseEntity<Object> result = advice.handleNotReadableBody(unreadable);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertEquals("Malformed request body", result.getBody());
    }
}
