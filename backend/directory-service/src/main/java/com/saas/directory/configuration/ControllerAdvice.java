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
    private static final Logger logger = LoggerFactory.getLogger(ControllerAdvice.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException e) {
        logger.debug("Access denied: ", e);
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
        logger.debug("Rejected invalid request payload: fields [{}]", fields);
        return new ResponseEntity<>("Invalid request payload: " + fields, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleArgumentException(IllegalArgumentException e) {
        logger.debug("Exception caught in controller: ", e);
        return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
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
            logger.debug("Rejected malformed request body: ", e);
            return new ResponseEntity<>(guardFailure.getMessage(), HttpStatus.BAD_REQUEST);
        }
        logger.debug("Rejected unreadable request body: ", e);
        return new ResponseEntity<>("Malformed request body", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ResourceNotFoundException e) {
        logger.debug("Exception caught in controller: ", e);
        return new ResponseEntity<>(e.getMessage(), e.getHttpStatus());
    }

    @ExceptionHandler(AsaasApiException.class)
    public ResponseEntity<Object> handleAsaasApiException(AsaasApiException e) {
        logger.error("Asaas API error: status={}, message={}", e.getHttpStatus(), e.getMessage(), e);
        return new ResponseEntity<>(e.getMessage(), e.getHttpStatus());
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<Object> handleResourceAlreadyExistsException(ResourceAlreadyExistsException e) {
        logger.debug("Exception caught in controller: ", e);
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
        logger.error("Data integrity violation: ", e);
        return new ResponseEntity<>("Resource conflict", HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalAccessException.class)
    public ResponseEntity<Object> illegalAccessException(IllegalAccessException e) {
        // Security: auth-decision messages historically echoed the presented JWT — return a static
        // body so bearer credentials never leak into responses.
        logger.debug("Access denied: ", e);
        return new ResponseEntity<>("Invalid or expired token", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e) {
        logger.error("Exception caught in controller: ", e);
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
