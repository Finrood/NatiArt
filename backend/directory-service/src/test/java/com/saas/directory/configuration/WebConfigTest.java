package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * CORS origins come from the {@code nati.cors.allowed-origins} property so an
 * origin change needs no rebuild; the bean must reflect exactly what was
 * configured.
 */
class WebConfigTest {

    @Test
    void corsFilter_reflectsConfiguredOrigins() {
        final List<String> origins = List.of("https://shop.example.com", "http://localhost:4200");
        final CorsConfigurationSource source = new WebConfig(origins).corsFilter();

        final CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/users"));

        assertNotNull(config, "Expected a CORS configuration for /**");
        assertEquals(origins, config.getAllowedOrigins());
    }

    @Test
    void corsFilter_allowsCredentials() {
        final CorsConfigurationSource source = new WebConfig(List.of("https://shop.example.com")).corsFilter();

        final CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/login"));

        assertNotNull(config, "Expected a CORS configuration for /**");
        assertTrue(Boolean.TRUE.equals(config.getAllowCredentials()));
    }
}
