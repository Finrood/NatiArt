package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

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
}
