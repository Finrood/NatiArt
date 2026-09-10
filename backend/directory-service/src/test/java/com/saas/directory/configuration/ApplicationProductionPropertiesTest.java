package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * The production profile must never inherit the development CORS origin: a prod
 * boot without {@code CORS_ALLOWED_ORIGINS} set must allow the storefront
 * origin only, never {@code localhost}.
 */
class ApplicationProductionPropertiesTest {

    @Test
    void productionCorsDefault_isProdOnly() throws Exception {
        final String content = readProductionProperties();

        assertTrue(content.contains("nati.cors.allowed-origins"), "Expected a production CORS override");
        assertTrue(
                content.contains("https://natiart.samuelpetre.com"),
                "Expected the production CORS default to allow the storefront origin");
        assertFalse(content.contains("localhost"), "Production CORS default must not allow localhost");
    }

    private static String readProductionProperties() throws Exception {
        try (InputStream stream = ApplicationProductionPropertiesTest.class
                .getClassLoader()
                .getResourceAsStream("application-production.properties")) {
            assertNotNull(stream, "Expected application-production.properties on the classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
