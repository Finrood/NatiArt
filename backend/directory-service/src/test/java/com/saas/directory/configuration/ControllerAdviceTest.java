package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.saas.directory.controller.helper.ResourceAlreadyExistsException;
import com.saas.directory.dto.UserRegistrationDto;

class ControllerAdviceTest {

    private ControllerAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new ControllerAdvice();
    }

    @Test
    void handleResourceAlreadyExistsException_returns409() {
        final ResponseEntity<Object> result =
                advice.handleResourceAlreadyExistsException(new ResourceAlreadyExistsException("taken"));

        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
        assertEquals("taken", result.getBody());
    }

    @Test
    void handleDataIntegrityViolation_returns409WithStaticBody() {
        final DataIntegrityViolationException duplicate = new DataIntegrityViolationException(
                "could not execute statement [insert into users (username) values (?)]");

        final ResponseEntity<Object> result = advice.handleDataIntegrityViolation(duplicate);

        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
        assertEquals("Resource conflict", result.getBody());
    }

    @Test
    void handleException_returnsStaticMessageAnd500() {
        final ResponseEntity<Object> result = advice.handleException(new RuntimeException("select * from users"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertEquals("Internal server error", result.getBody());
    }

    @Test
    void handleAccessDeniedException_returns403WithStaticBody() {
        final ResponseEntity<Object> result = advice.handleAccessDeniedException(new AccessDeniedException("denied"));

        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
        assertEquals("Access denied", result.getBody());
    }

    @Test
    void illegalAccessException_returns401WithStaticBodyWithoutLeakingTheToken() {
        final String leaked = "eyJhbGciOiJIUzI1NiJ9.payload.signature";
        final ResponseEntity<Object> result = advice.illegalAccessException(
                new IllegalAccessException("Authentication Token [" + leaked + "] is not valid"));

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertEquals(ControllerAdvice.INVALID_TOKEN_MESSAGE, result.getBody());
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
                "JSON parse error", new IllegalArgumentException("Username is required"));

        final ResponseEntity<Object> result = advice.handleNotReadableBody(unreadable);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertEquals("Username is required", result.getBody());
    }

    @Test
    void handleNotReadableBody_mapsUnrelatedParseErrorsToGeneric400() {
        final HttpMessageNotReadableException unreadable = new HttpMessageNotReadableException("JSON parse error");

        final ResponseEntity<Object> result = advice.handleNotReadableBody(unreadable);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertEquals("Malformed request body", result.getBody());
    }

    @Test
    void handleMethodArgumentNotValid_returns400ListingFieldNamesOnly() throws NoSuchMethodException {
        final MethodParameter parameter = new MethodParameter(
                ControllerAdviceTest.class.getDeclaredMethod("sample", UserRegistrationDto.class), 0);
        final MapBindingResult bindingResult = new MapBindingResult(new HashMap<>(), "userRegistrationDto");
        bindingResult.rejectValue("username", "NotBlank");
        bindingResult.rejectValue("profile.lastname", "NotBlank");

        final ResponseEntity<Object> result =
                advice.handleMethodArgumentNotValid(new MethodArgumentNotValidException(parameter, bindingResult));

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        final String body = (String) result.getBody();
        assertTrue(body.contains("profile.lastname"));
        assertTrue(body.contains("username"));
        // the rejected values are client input and must never be echoed back
        assertFalse(body.contains("attacker@example.com"));
        assertFalse(body.contains("secret-value"));
    }

    @SuppressWarnings("unused")
    private static void sample(UserRegistrationDto dto) {}
}
