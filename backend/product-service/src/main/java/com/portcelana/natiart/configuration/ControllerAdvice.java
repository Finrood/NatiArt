package com.portcelana.natiart.configuration;

import jakarta.persistence.OptimisticLockException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.portcelana.natiart.controller.helper.ResourceAlreadyExistsException;
import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import com.portcelana.natiart.service.AsaasApiException;
import com.portcelana.natiart.service.UpstreamServiceException;

@org.springframework.web.bind.annotation.ControllerAdvice
public class ControllerAdvice {
    private static final Logger LOGGER = LoggerFactory.getLogger(ControllerAdvice.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException e) {
        LOGGER.debug("Access denied: ", e);
        return new ResponseEntity<>("Access denied", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e) {
        LOGGER.error("Exception caught in controller: ", e);
        return new ResponseEntity<>("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
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
     * Jackson wraps {@code @JsonCreator} guard failures (e.g. our
     * {@code IllegalArgumentException}s) in {@code ValueInstantiationException},
     * surfacing as {@code HttpMessageNotReadableException} -- without this
     * handler the catch-all below would render those client errors as 500s.
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

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<Object> handleResourceAlreadyExistsException(ResourceAlreadyExistsException e) {
        LOGGER.debug("Exception caught in controller: ", e);
        return new ResponseEntity<>(e.getMessage(), e.getHttpStatus());
    }

    @ExceptionHandler(UserNotAllowedException.class)
    public ResponseEntity<Object> handleResourceUserNotAllowedException(UserNotAllowedException e) {
        LOGGER.debug("Exception caught in controller: ", e);
        return new ResponseEntity<>(e.getMessage(), e.getHttpStatus());
    }

    /**
     * Unusable upstream payment-provider responses (missing or malformed
     * fields) are a 502 with the exception's static message -- the message is
     * reflected to the caller, so throw sites must never embed raw upstream
     * text (see {@code AsaasApiException}).
     */
    @ExceptionHandler(AsaasApiException.class)
    public ResponseEntity<Object> handleAsaasApiException(AsaasApiException e) {
        LOGGER.error("Asaas API error: status={}, message={}", e.getHttpStatus(), e.getMessage(), e);
        return new ResponseEntity<>(e.getMessage(), e.getHttpStatus());
    }

    /**
     * Maps external-service transport and status failures to safe responses.
     * Only the provider's explicitly supported {@code Retry-After} value is
     * forwarded; provider response bodies never leave the server.
     */
    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<Object> handleUpstreamServiceException(UpstreamServiceException e) {
        LOGGER.error("Upstream service error: status={}, message={}", e.getHttpStatus(), e.getMessage(), e);
        final org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        if (e.getRetryAfter() != null) {
            headers.set(org.springframework.http.HttpHeaders.RETRY_AFTER, e.getRetryAfter());
        }
        return new ResponseEntity<>(e.getMessage(), headers, e.getHttpStatus());
    }

    /**
     * Concurrent full updates to a versioned row (e.g. `Product.updateProduct`)
     * fail the loser's commit instead of silently mixing fields. A lost update
     * is a client-retryable conflict, not a server error. Both the JPA and the
     * Spring-translated failure shapes map here.
     */
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<Object> handleOptimisticLockingFailure(RuntimeException e) {
        LOGGER.debug("Concurrent update conflict: ", e);
        return new ResponseEntity<>("Resource was modified concurrently", HttpStatus.CONFLICT);
    }

    /**
     * Backstop for check-then-act races (concurrent first-adds of one cart
     * line): both racers miss the row and the loser trips the
     * (username, product) unique constraint. A 409 conflict, not a 500 --
     * mirrors the manager-level translation in `CategoryManagerImpl`. Static
     * body so constraint SQL never leaks; error-logged for the server-side
     * signal.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        LOGGER.error("Data integrity violation: ", e);
        return new ResponseEntity<>("Resource conflict", HttpStatus.CONFLICT);
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
