package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class AuthCacheInvalidationClientTest {
    @Test
    void sendsAuthenticatedInvalidationToProductServiceAfterAccountChange() throws Exception {
        final HttpServer product = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        final AtomicReference<String> secret = new AtomicReference<>();
        final AtomicReference<String> path = new AtomicReference<>();
        product.createContext("/internal/auth-cache/users/", exchange -> {
            secret.set(exchange.getRequestHeaders().getFirst("X-NatiArt-Internal-Secret"));
            path.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        product.start();
        try {
            final AuthCacheInvalidationClient client = new AuthCacheInvalidationClient(
                    "http://localhost:" + product.getAddress().getPort() + "/", "shared-secret");
            assertTrue(client.invalidateUser("user-1"));
            assertEquals("shared-secret", secret.get());
            assertEquals("/internal/auth-cache/users/user-1/invalidate", path.get());
        } finally {
            product.stop(0);
        }
    }

    @Test
    void configuredProductServiceRequiresSharedSecret() {
        assertThrows(IllegalStateException.class, () -> new AuthCacheInvalidationClient("http://localhost:8082", ""));
    }
}
