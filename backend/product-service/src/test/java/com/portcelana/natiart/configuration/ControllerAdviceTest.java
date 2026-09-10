package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.OptimisticLockException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;

import com.portcelana.natiart.service.AsaasApiException;

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
    void handleOptimisticLockingFailure_springShape_returns409WithStaticBody() {
        final ResponseEntity<Object> result = advice.handleOptimisticLockingFailure(
                new OptimisticLockingFailureException("Batch update row count wrong"));

        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
        assertEquals("Resource was modified concurrently", result.getBody());
    }

    @Test
    void handleOptimisticLockingFailure_jakartaShape_returns409WithStaticBody() {
        final ResponseEntity<Object> result =
                advice.handleOptimisticLockingFailure(new OptimisticLockException("Row was updated"));

        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
        assertEquals("Resource was modified concurrently", result.getBody());
    }

    @Test
    void handleDataIntegrityViolation_returns409WithStaticBody() {
        final DataIntegrityViolationException duplicate = new DataIntegrityViolationException(
                "could not execute statement [insert into cart_item (username, product_id) values (?, ?)]");

        final ResponseEntity<Object> result = advice.handleDataIntegrityViolation(duplicate);

        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
        assertEquals("Resource conflict", result.getBody());
    }

    @Test
    void handleNumberFormatException_returns400WithStaticBodyHidingParsingArtifacts() {
        final ResponseEntity<Object> result =
                advice.handleNumberFormatException(new NumberFormatException("For input string: \"abc\""));

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertEquals("Invalid request", result.getBody());
    }

    @Test
    void handleArgumentException_returns400WithTheDeliberateValidationMessage() {
        final ResponseEntity<Object> result =
                advice.handleArgumentException(new IllegalArgumentException("Username cannot be empty"));

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertEquals("Username cannot be empty", result.getBody());
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

    @Test
    void handleAsaasApiException_returnsCarriedStatusWithStaticBody() {
        final ResponseEntity<Object> result = advice.handleAsaasApiException(
                new AsaasApiException("Invalid payment provider response", HttpStatus.BAD_GATEWAY));

        assertEquals(HttpStatus.BAD_GATEWAY, result.getStatusCode());
        assertEquals("Invalid payment provider response", result.getBody());
    }
}
