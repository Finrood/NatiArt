package com.saas.directory.configuration;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.saas.directory.controller.helper.ResourceAlreadyExistsException;
import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.service.AsaasApiException;

@org.springframework.web.bind.annotation.ControllerAdvice
public class ControllerAdvice {
    private static final Logger LOGGER = LoggerFactory.getLogger(ControllerAdvice.class);

    /**
     * Static body for every invalid-token denial, shared by the filter
     * ({@link JwtAuthFilter}) and this advice so one failure has one contract:
     * 401 with this body. Authenticated-but-forbidden denials
     * ({@code AccessDeniedException}) stay 403 "Access denied" — a different
     * failure must not share a shape with an invalid credential.
     */
    public static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException e) {
        LOGGER.debug("Access denied: ", e);
        return new ResponseEntity<>("Access denied", HttpStatus.FORBIDDEN);
    }

    /**
     * Bean-validation failures on {@code @Valid} request payloads answer with 400
     * and list only the offending field names -- never the rejected values, which
     * are client input and must not be echoed into the response body.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        final String fields = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
        LOGGER.debug("Rejected invalid request payload: fields [{}]", fields);
        return new ResponseEntity<>("Invalid request payload: " + fields, HttpStatus.BAD_REQUEST);
    }

    /**
     * Machine-generated {@code IllegalArgumentException}s -- a
     * {@code NumberFormatException} for non-numeric request input, an
     * {@code Enum.valueOf} miss echoing the enum's constant list -- carry
     * server-side parsing artifacts, not client-facing validation messages.
     * The generic handler below also uses a static body: only the explicit
     * request-body guard path is allowed to return a deliberate validation
     * message to a client.
     */
    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<Object> handleNumberFormatException(NumberFormatException e) {
        LOGGER.debug("Rejected non-numeric request input: {}", e.getMessage());
        return new ResponseEntity<>("Invalid request", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleArgumentException(IllegalArgumentException e) {
        LOGGER.debug("Rejected invalid request: {}", e.getMessage(), e);
        return new ResponseEntity<>("Invalid request", HttpStatus.BAD_REQUEST);
    }

    /**
     * Jackson deserialization failures (e.g. guard rejections wrapped in
     * {@code ValueInstantiationException}) surface as
     * {@code HttpMessageNotReadableException} -- without this handler the
     * catch-all below would render those client errors as 500s. Mirrors the
     * product-service advice so both services answer malformed bodies with 400.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleNotReadableBody(HttpMessageNotReadableException e) {
        final IllegalArgumentException guardFailure = findIllegalArgumentCause(e);
        if (guardFailure != null && guardFailure.getMessage() != null) {
            LOGGER.debug("Rejected malformed request body: ", e);
            return new ResponseEntity<>(guardFailure.getMessage(), HttpStatus.BAD_REQUEST);
        }
        LOGGER.debug("Rejected unreadable request body: ", e);
        return new ResponseEntity<>("Malformed request body", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ResourceNotFoundException e) {
        LOGGER.debug("Exception caught in controller: ", e);
        return new ResponseEntity<>(e.getMessage(), e.getHttpStatus());
    }

    @ExceptionHandler(AsaasApiException.class)
    public ResponseEntity<Object> handleAsaasApiException(AsaasApiException e) {
        LOGGER.error("Asaas API error: status={}, message={}", e.getHttpStatus(), e.getMessage(), e);
        return new ResponseEntity<>(e.getMessage(), e.getHttpStatus());
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<Object> handleResourceAlreadyExistsException(ResourceAlreadyExistsException e) {
        LOGGER.debug("Exception caught in controller: ", e);
        return new ResponseEntity<>(e.getMessage(), e.getHttpStatus());
    }

    /**
     * Backstop for check-then-act races (concurrent duplicate registrations):
     * both racers pass the pre-save existence check and the loser trips the
     * unique constraint. That is a 409 conflict, not a 500 -- same contract
     * as the pre-check rejection. The body stays static so constraint SQL
     * (table/column names) never leaks; the error log below keeps the
     * server-side signal for persistent streams.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        LOGGER.error("Data integrity violation: ", e);
        return new ResponseEntity<>("Resource conflict", HttpStatus.CONFLICT);
    }

    /**
     * Invalid-token denials reaching a handler (e.g. {@code POST /refresh-token}
     * with an expired or bogus refresh token) answer with the same contract as
     * the filter-level denial ({@link JwtAuthFilter}): 401 + the static
     * {@link #INVALID_TOKEN_MESSAGE} body — not 403, which is reserved for
     * authenticated-but-forbidden callers.
     */
    @ExceptionHandler(IllegalAccessException.class)
    public ResponseEntity<Object> illegalAccessException(IllegalAccessException e) {
        // Security: auth-decision messages historically echoed the presented JWT — return a static
        // body so bearer credentials never leak into responses.
        LOGGER.debug("Access denied: ", e);
        return new ResponseEntity<>(INVALID_TOKEN_MESSAGE, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e) {
        LOGGER.error("Exception caught in controller: ", e);
        return new ResponseEntity<>("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static IllegalArgumentException findIllegalArgumentCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IllegalArgumentException illegalArgument) {
                return illegalArgument;
            }
            current = current.getCause();
        }
        return null;
    }
}
