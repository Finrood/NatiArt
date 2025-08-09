package com.saas.directory.configuration;

import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.service.AsaasApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

@org.springframework.web.bind.annotation.ControllerAdvice
public class ControllerAdvice {
    private final static Logger logger = LoggerFactory.getLogger(ControllerAdvice.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleArgumentException(IllegalArgumentException e) {
        logger.debug("Exception caught in controller: ", e);
        return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
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
}
