package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.saas.directory.controller.helper.ResourceAlreadyExistsException;

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
    void handleException_returnsStaticMessageAnd500() {
        final ResponseEntity<Object> result = advice.handleException(new RuntimeException("select * from users"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertEquals("Internal server error", result.getBody());
    }
}
