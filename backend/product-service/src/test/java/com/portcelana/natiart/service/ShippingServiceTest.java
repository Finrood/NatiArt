package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import com.portcelana.natiart.dto.shipping.ShippingEstimateRequest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ShippingServiceTest {

    @Test
    void constructor_rejectsBlankApiToken() {
        assertThrows(
                IllegalStateException.class,
                () -> new ShippingService("https://api.example.com/calculate", "  ", "88085201"));
    }

    @Test
    void constructor_rejectsNullApiToken() {
        assertThrows(
                IllegalStateException.class,
                () -> new ShippingService("https://api.example.com/calculate", null, "88085201"));
    }

    @Test
    void constructor_rejectsBlankFromPostalCode() {
        assertThrows(
                IllegalStateException.class,
                () -> new ShippingService("https://api.example.com/calculate", "test-token", "  "));
    }

    @Test
    void constructor_rejectsNullFromPostalCode() {
        assertThrows(
                IllegalStateException.class,
                () -> new ShippingService("https://api.example.com/calculate", "test-token", null));
    }

    @Test
    void constructor_acceptsConfiguredApiToken() {
        assertDoesNotThrow(() -> new ShippingService("https://api.example.com/calculate", "test-token", "88085201"));
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
    void mapShippingError_mapsUnexpectedUpstreamFailuresToBadGateway() {
        final HttpServerErrorException upstream =
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Bad Gateway", null, null, null);
        final UpstreamServiceException mapped =
                assertInstanceOf(UpstreamServiceException.class, ShippingService.mapShippingError(upstream));
        assertEquals(HttpStatus.BAD_GATEWAY, mapped.getHttpStatus());
        assertEquals("Shipping provider unavailable", mapped.getMessage());
    }

    @Test
    void mapShippingError_mapsRateLimitAndPreservesRetryAfter() {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "11");
        final HttpClientErrorException upstream =
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers, null, null);

        final UpstreamServiceException mapped =
                assertInstanceOf(UpstreamServiceException.class, ShippingService.mapShippingError(upstream));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, mapped.getHttpStatus());
        assertEquals("11", mapped.getRetryAfter());
    }

    @Test
    void getShippingEstimates_mapsTransportFailureToServiceUnavailableAfterBoundedRetry() {
        final RestTemplate restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
        final ResourceAccessException upstream = new ResourceAccessException("read timed out");
        org.mockito.Mockito.doThrow(upstream)
                .when(restTemplate)
                .exchange(
                        org.mockito.ArgumentMatchers.eq("https://api.example.com/calculate"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.POST),
                        org.mockito.ArgumentMatchers.any(HttpEntity.class),
                        org.mockito.ArgumentMatchers.any(ParameterizedTypeReference.class));

        final UpstreamServiceException mapped = org.junit.jupiter.api.Assertions.assertThrows(
                UpstreamServiceException.class,
                () -> new ShippingService("https://api.example.com/calculate", "test-token", "88085201", restTemplate)
                        .getShippingEstimates(new ShippingEstimateRequest("88010000", 1.0f, 20.0f, 15.0f, 10.0f, 1)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, mapped.getHttpStatus());
        org.mockito.Mockito.verify(restTemplate, org.mockito.Mockito.times(3))
                .exchange(
                        org.mockito.ArgumentMatchers.eq("https://api.example.com/calculate"),
                        org.mockito.ArgumentMatchers.eq(HttpMethod.POST),
                        org.mockito.ArgumentMatchers.any(HttpEntity.class),
                        org.mockito.ArgumentMatchers.any(ParameterizedTypeReference.class));
    }

    @Test
    void mapShippingError_warnLogsUpstreamStatusAndBodyOnFallThrough() {
        final byte[] body = "{\"errors\":[\"validation-failed-marker\"]}".getBytes(StandardCharsets.UTF_8);
        final HttpClientErrorException upstream = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null, body, StandardCharsets.UTF_8);

        final RuntimeException[] mapped = new RuntimeException[1];
        final List<ILoggingEvent> events =
                captureLogEvents(ShippingService.class, () -> mapped[0] = ShippingService.mapShippingError(upstream));

        assertInstanceOf(UpstreamServiceException.class, mapped[0]);
        assertEquals(1, events.size());
        assertEquals(Level.WARN, events.get(0).getLevel());
        final String message = events.get(0).getFormattedMessage();
        assertTrue(message.contains("400"));
        assertTrue(message.contains("validation-failed-marker"));
    }

    @Test
    void mapShippingError_mappedMessagesStayStaticWithoutUpstreamBody() {
        final byte[] body = "{\"errors\":[\"validation-failed-marker\"]}".getBytes(StandardCharsets.UTF_8);
        final HttpClientErrorException upstream = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", null, body, StandardCharsets.UTF_8);

        final RuntimeException mapped = ShippingService.mapShippingError(upstream);

        assertEquals("Unauthorized api call to the shipping provider", mapped.getMessage());
        assertFalse(mapped.getMessage().contains("validation-failed-marker"));
    }

    private List<ILoggingEvent> captureLogEvents(Class<?> loggedClass, Runnable action) {
        final Logger logger = (Logger) LoggerFactory.getLogger(loggedClass);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        final Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            action.run();
            return List.copyOf(appender.list);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }
}
