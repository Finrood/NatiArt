package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.saas.directory.dto.ProfileDto;
import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
class AsaasUserManagerTest {

    private static final String CUSTOMERS_URL = "https://sandbox.asaas.com/api/v3/customers";

    @Test
    void constructor_rejectsBlankApiKey() {
        assertThrows(IllegalStateException.class, () -> new AsaasUserManager("  ", CUSTOMERS_URL));
        assertThrows(IllegalStateException.class, () -> new AsaasUserManager(null, CUSTOMERS_URL));
    }

    @Test
    void constructor_acceptsConfiguredApiKey() {
        assertDoesNotThrow(() -> new AsaasUserManager("test-api-key", CUSTOMERS_URL));
    }

    @Test
    void mapAsaasError_returnsStaticMessageWithoutUpstreamBody() {
        final String sensitiveBody =
                "cpf=12345678909 email=synthetic@example.invalid\nFORGED_LOG " + "x".repeat(20_000);
        final HttpClientErrorException upstream = HttpClientErrorException.create(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Unprocessable Entity",
                null,
                sensitiveBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        final Logger logger = (Logger) LoggerFactory.getLogger(AsaasUserManager.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        final AsaasApiException result;
        final List<ILoggingEvent> events;
        try {
            result = AsaasUserManager.mapAsaasError(upstream);
            events = List.copyOf(appender.list);
        } finally {
            logger.detachAppender(appender);
        }
        assertEquals("Customer registration failed at the payment provider", result.getMessage());
        assertEquals(1, events.size());
        final String logged = events.getFirst().getFormattedMessage();
        assertTrue(logged.contains("providerStatusCode=422"));
        assertTrue(logged.contains("responseBodyBytes=8192"));
        assertFalse(logged.contains("12345678909"));
        assertFalse(logged.contains("synthetic@example.invalid"));
        assertFalse(logged.contains("FORGED_LOG"));
        assertTrue(logged.length() < 200);
    }

    @Test
    void mapAsaasError_preservesUpstreamStatus() {
        final HttpClientErrorException unauthorized =
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null);
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                AsaasUserManager.mapAsaasError(unauthorized).getHttpStatus());
        final HttpClientErrorException unprocessable = HttpClientErrorException.create(
                HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", null, null, null);
        assertEquals(
                HttpStatus.UNPROCESSABLE_ENTITY,
                AsaasUserManager.mapAsaasError(unprocessable).getHttpStatus());
    }

    @Test
    void registerUser_preservesRetryableServerFailures() throws Exception {
        final RestTemplate restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
        final HttpServerErrorException upstream =
                HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway", null, null, null);
        org.mockito.Mockito.when(restTemplate.postForObject(
                        org.mockito.ArgumentMatchers.eq(CUSTOMERS_URL),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(AsaasCustomerCreationResponse.class)))
                .thenThrow(upstream);

        final HttpServerErrorException thrown = assertThrows(
                HttpServerErrorException.class,
                () -> new AsaasUserManager("test-api-key", CUSTOMERS_URL, restTemplate).registerUser(validUser()));

        assertEquals(HttpStatus.BAD_GATEWAY, thrown.getStatusCode());
    }

    @Test
    void registerUser_preservesRetryableTransportFailures() {
        final RestTemplate restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
        final ResourceAccessException upstream = new ResourceAccessException("connection refused");
        org.mockito.Mockito.when(restTemplate.postForObject(
                        org.mockito.ArgumentMatchers.eq(CUSTOMERS_URL),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(AsaasCustomerCreationResponse.class)))
                .thenThrow(upstream);

        final ResourceAccessException thrown = assertThrows(
                ResourceAccessException.class,
                () -> new AsaasUserManager("test-api-key", CUSTOMERS_URL, restTemplate).registerUser(validUser()));

        assertEquals("connection refused", thrown.getMessage());
    }

    private UserDto validUser() {
        return new UserDto()
                .setUsername("test@example.com")
                .setProfile(new ProfileDto()
                        .setFirstname("Test")
                        .setLastname("User")
                        .setCpf("12345678909")
                        .setPhone("48999999999")
                        .setCountry("Brazil")
                        .setState("SC")
                        .setCity("Florianopolis")
                        .setNeighborhood("Centro")
                        .setZipCode("88000000")
                        .setStreet("Main Street"));
    }
}
