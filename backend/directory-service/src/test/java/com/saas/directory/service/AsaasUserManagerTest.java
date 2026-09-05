package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
