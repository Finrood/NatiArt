package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import com.portcelana.natiart.model.Payment;

class AsaasChargeSafetyHttpTest {
    @Test
    void realPendingJsonWithoutOptionalFlagsMustBeDeletedBeforeRelease() throws Exception {
        try (Provider provider = new Provider("{\"id\":\"pay_1\",\"customer\":\"cus_1\",\"status\":\"PENDING\"}")) {
            assertDoesNotThrow(() -> provider.service().ensureChargeInactive(new Payment("pay_1", "cus_1", "order-1")));
            assertEquals(1, provider.gets.get());
            assertEquals(1, provider.deletes.get());
            assertEquals("inert-fixture-key", provider.key.get());
        }
    }

    @Test
    void actualPaidOrForeignJsonNeverAuthorizesDelete() throws Exception {
        for (String body : new String[] {
            "{\"id\":\"pay_1\",\"customer\":\"cus_1\",\"status\":\"RECEIVED\"}",
            "{\"id\":\"pay_1\",\"customer\":\"cus_other\",\"status\":\"PENDING\"}"
        }) {
            try (Provider provider = new Provider(body)) {
                assertThrows(
                        IllegalStateException.class,
                        () -> provider.service().ensureChargeInactive(new Payment("pay_1", "cus_1", "order-1")));
                assertEquals(1, provider.gets.get());
                assertEquals(0, provider.deletes.get());
            }
        }
    }

    @Test
    void malformedActualHttpResponseKeepsReservationProtected() throws Exception {
        try (Provider provider = new Provider("{invalid")) {
            assertThrows(
                    UpstreamServiceException.class,
                    () -> provider.service().ensureChargeInactive(new Payment("pay_1", "cus_1", "order-1")));
            assertEquals(1, provider.gets.get());
            assertEquals(0, provider.deletes.get());
        }
    }

    private static final class Provider implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger gets = new AtomicInteger();
        private final AtomicInteger deletes = new AtomicInteger();
        private final AtomicReference<String> key = new AtomicReference<>();

        Provider(String body) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/payments/pay_1", exchange -> {
                key.set(exchange.getRequestHeaders().getFirst("access_token"));
                final String response;
                if ("GET".equals(exchange.getRequestMethod())) {
                    gets.incrementAndGet();
                    response = body;
                } else if ("DELETE".equals(exchange.getRequestMethod())) {
                    deletes.incrementAndGet();
                    response = "{\"id\":\"pay_1\",\"deleted\":true}";
                } else {
                    exchange.sendResponseHeaders(405, -1);
                    exchange.close();
                    return;
                }
                final byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
                exchange.close();
            });
            server.start();
        }

        AsaasChargeSafetyService service() {
            return new AsaasChargeSafetyService(
                    "inert-fixture-key",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/payments");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
