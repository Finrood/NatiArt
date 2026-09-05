package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.controller.helper.UserNotAllowedException;

class ShippingServiceTest {

    @Test
    void constructor_rejectsBlankApiToken() {
        assertThrows(IllegalStateException.class, () -> new ShippingService("https://api.example.com/calculate", "  "));
    }

    @Test
    void constructor_rejectsNullApiToken() {
        assertThrows(IllegalStateException.class, () -> new ShippingService("https://api.example.com/calculate", null));
    }

    @Test
    void constructor_acceptsConfiguredApiToken() {
        assertDoesNotThrow(() -> new ShippingService("https://api.example.com/calculate", "test-token"));
    }

    @Test
    void mapShippingError_mapsAuthFailuresToUserNotAllowed() {
        assertThrows(UserNotAllowedException.class, () -> {
            throw ShippingService.mapShippingError(
                    HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));
        });
        assertThrows(UserNotAllowedException.class, () -> {
            throw ShippingService.mapShippingError(
                    HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null));
        });
    }

    @Test
    void mapShippingError_mapsMissingEstimateToNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> {
            throw ShippingService.mapShippingError(
                    HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));
        });
    }

    @Test
    void mapShippingError_passesThroughUnexpectedUpstreamFailures() {
        final HttpServerErrorException upstream =
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Bad Gateway", null, null, null);
        assertSame(upstream, ShippingService.mapShippingError(upstream));
    }
}
