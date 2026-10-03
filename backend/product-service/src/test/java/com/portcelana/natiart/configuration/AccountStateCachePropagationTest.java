package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.WebClient;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import com.portcelana.natiart.controller.AuthCacheInvalidationController;
import com.portcelana.natiart.model.TokenValidationCacheEntry;
import com.portcelana.natiart.repository.TokenValidationCacheRepository;

class AccountStateCachePropagationTest {
    private static final String ADMIN_RESPONSE = """
            {"authenticated":true,"name":"admin","authorities":[{"authority":"ROLE_ADMIN"}],
             "principal":{"id":"user-1","username":"admin","role":"ADMIN"}}
            """;

    private HttpServer directory;

    @AfterEach
    void stopDirectory() {
        if (directory != null) {
            directory.stop(0);
        }
        SecurityContextHolder.clearContext();
    }

    @Test
    void warmedAdminTokenIsRejectedAfterAccountChangeInvalidatesProductCache() throws Exception {
        final Map<String, TokenValidationCacheEntry> rows = new ConcurrentHashMap<>();
        final TokenValidationCacheRepository repository = mock(TokenValidationCacheRepository.class);
        when(repository.findValid(any(), anyLong())).thenAnswer(invocation -> {
            final TokenValidationCacheEntry row = rows.get(invocation.getArgument(0));
            return row != null && row.getExpiresAt() > invocation.getArgument(1, Long.class)
                    ? Optional.of(row)
                    : Optional.empty();
        });
        when(repository.save(any(TokenValidationCacheEntry.class))).thenAnswer(invocation -> {
            final TokenValidationCacheEntry row = invocation.getArgument(0);
            rows.put(row.getTokenDigest(), row);
            return row;
        });
        when(repository.deleteByUserId(eq("user-1"))).thenAnswer(invocation -> {
            final int before = rows.size();
            rows.values().removeIf(row -> "user-1".equals(row.getUserId()));
            return before - rows.size();
        });
        final DatabaseTokenValidationCache cache =
                new DatabaseTokenValidationCache(repository, new ObjectMapper(), 30_000, 100);
        final AuthCacheInvalidationController callback = new AuthCacheInvalidationController(cache, "shared-secret");
        final MockMvc callbackHttp = MockMvcBuilders.standaloneSetup(callback).build();

        final AtomicInteger directoryCalls = new AtomicInteger();
        final java.util.concurrent.atomic.AtomicBoolean revoked = new java.util.concurrent.atomic.AtomicBoolean();
        directory = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        directory.createContext("/validate-token", exchange -> {
            directoryCalls.incrementAndGet();
            if (revoked.get()) {
                exchange.sendResponseHeaders(401, -1);
            } else {
                final byte[] body = ADMIN_RESPONSE.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        directory.start();
        final JwtAuthFilter filter = new JwtAuthFilter(
                WebClient.builder(),
                "http://localhost:" + directory.getAddress().getPort(),
                cache);
        final String token = JWT.create()
                .withExpiresAt(Instant.now().plusSeconds(120))
                .sign(Algorithm.HMAC256("synthetic-test-secret"));

        assertAdmin(filter, token);
        assertAdmin(filter, token);
        assertEquals(1, directoryCalls.get(), "the second request must use the warmed cache");
        assertEquals(1, rows.size());

        revoked.set(true);
        final String callbackPath = "/internal/auth-cache/users/user-1/invalidate";
        assertEquals(
                403,
                callbackHttp
                        .perform(MockMvcRequestBuilders.post(callbackPath)
                                .header(AuthCacheInvalidationController.SECRET_HEADER, "wrong-secret"))
                        .andReturn()
                        .getResponse()
                        .getStatus());
        assertEquals(1, rows.size(), "an unauthorized callback cannot evict entries");
        assertEquals(
                204,
                callbackHttp
                        .perform(MockMvcRequestBuilders.post(callbackPath)
                                .header(AuthCacheInvalidationController.SECRET_HEADER, "shared-secret"))
                        .andReturn()
                        .getResponse()
                        .getStatus());
        assertTrue(rows.isEmpty());

        SecurityContextHolder.clearContext();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request(token), response, chain);
        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
        assertEquals(2, directoryCalls.get(), "an unexpired token must be revalidated after revocation");
    }

    private void assertAdmin(JwtAuthFilter filter, String token) throws IOException, jakarta.servlet.ServletException {
        SecurityContextHolder.clearContext();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request(token), response, chain);
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
    }

    private MockHttpServletRequest request(String token) {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/product");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
