package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.model.TokenValidationCacheEntry;
import com.portcelana.natiart.repository.TokenValidationCacheRepository;

@DataJpaTest(properties = {"spring.sql.init.mode=never", "natiart.test.contract=TokenValidationCacheConcurrencyTest"})
class TokenValidationCacheConcurrencyTest {
    @Autowired
    private TokenValidationCacheRepository repository;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousColdRequestsRecoverTheActualCommittedUniqueKeyRace() throws Exception {
        final CyclicBarrier inserts = new CyclicBarrier(2);
        final AtomicInteger saves = new AtomicInteger();
        final TokenValidationCacheRepository coordinated =
                mock(TokenValidationCacheRepository.class, AdditionalAnswers.delegatesTo(repository));
        doAnswer(invocation -> {
                    final TokenValidationCacheEntry saved = repository.save(invocation.getArgument(0));
                    // Both persistence contexts queue a first insert before either commits.
                    if (saves.incrementAndGet() <= 2) inserts.await(10, TimeUnit.SECONDS);
                    return saved;
                })
                .when(coordinated)
                .save(any(TokenValidationCacheEntry.class));
        final DatabaseTokenValidationCache store =
                new DatabaseTokenValidationCache(coordinated, new ObjectMapper(), 30_000, 100);
        final TransactionTemplate transaction = new TransactionTemplate(transactions);
        final TokenValidationCache cache = mock(TokenValidationCache.class);
        when(cache.get(anyString()))
                .thenAnswer(invocation -> transaction.execute(status -> store.get(invocation.getArgument(0))));
        doAnswer(invocation -> {
                    transaction.executeWithoutResult(
                            status -> store.put(invocation.getArgument(0), invocation.getArgument(1)));
                    return null;
                })
                .when(cache)
                .put(anyString(), any(AuthenticationResponseDto.class));

        final HttpServer directory = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final AtomicInteger validations = new AtomicInteger();
        directory.createContext("/validate-token", exchange -> {
            validations.incrementAndGet();
            final byte[] body = """
                    {"authenticated":true,"authorities":[{"authority":"ROLE_USER"}],
                     "principal":{"id":"customer-1","username":"customer"},"name":"customer"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        directory.start();
        final String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        ("{\"exp\":" + (Instant.now().getEpochSecond() + 300) + "}").getBytes(StandardCharsets.UTF_8));
        final String token = "e30." + payload + ".signature";
        final JwtAuthFilter first = new JwtAuthFilter(
                WebClient.builder(),
                "http://127.0.0.1:" + directory.getAddress().getPort(),
                cache);
        final JwtAuthFilter second = new JwtAuthFilter(
                WebClient.builder(),
                "http://127.0.0.1:" + directory.getAddress().getPort(),
                cache);
        try (final var workers = Executors.newFixedThreadPool(2)) {
            final var firstRequest = workers.submit(() -> protectedRead(first, token));
            final var secondRequest = workers.submit(() -> protectedRead(second, token));
            assertEquals(200, firstRequest.get(15, TimeUnit.SECONDS));
            assertEquals(200, secondRequest.get(15, TimeUnit.SECONDS));
            assertEquals(2, validations.get());
            assertEquals(3, saves.get(), "one real failed insert must be retried in a fresh transaction");
            assertEquals(1, repository.count());
        } finally {
            directory.stop(0);
            transaction.executeWithoutResult(status -> repository.deleteAll());
        }
    }

    private int protectedRead(JwtAuthFilter filter, String token) throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.addHeader("Authorization", "Bearer " + token);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();
        try {
            filter.doFilter(request, response, chain);
            assertNotNull(chain.getRequest());
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            return response.getStatus();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
