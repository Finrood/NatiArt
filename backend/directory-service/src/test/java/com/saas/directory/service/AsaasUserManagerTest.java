package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
class AsaasUserManagerTest {

    @Test
    void constructor_rejectsBlankApiKey() {
        assertThrows(
                IllegalStateException.class,
                () -> new AsaasUserManager("  ", "https://sandbox.asaas.com/api/v3/customers"));
        assertThrows(
                IllegalStateException.class,
                () -> new AsaasUserManager(null, "https://sandbox.asaas.com/api/v3/customers"));
    }

    @Test
    void constructor_acceptsConfiguredApiKey() {
        assertDoesNotThrow(() -> new AsaasUserManager("test-api-key", "https://sandbox.asaas.com/api/v3/customers"));
    }

    @Test
    void mapAsaasError_returnsStaticMessageWithoutUpstreamBody() {
        final HttpClientErrorException upstream = HttpClientErrorException.create(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Unprocessable Entity",
                null,
                "{\"errors\":[\"LEAK_MARKER\"]}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        final AsaasApiException result = AsaasUserManager.mapAsaasError(upstream);
        assertEquals("Customer registration failed at the payment provider", result.getMessage());
        assertFalse(result.getMessage().contains("LEAK_MARKER"));
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
}
