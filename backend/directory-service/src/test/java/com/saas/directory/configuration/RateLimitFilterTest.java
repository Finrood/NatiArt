package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FixedWindowStore store;

    @BeforeEach
    void setUp() {
        store = new FixedWindowStore(Clock.systemUTC());
        filter = new RateLimitFilter(3, List.of(), store);
    }

    private MockHttpServletRequest post(String uri, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    void allowsRequestsUpToLimitThenReturns429() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(post("/login", "1.2.3.4"), response, new MockFilterChain());
            assertNotEquals(429, response.getStatus());
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(post("/login", "1.2.3.4"), blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    @Test
    void tracksClientsIndependentlyByIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(post("/login", "10.0.0." + i), response, new MockFilterChain());
            assertNotEquals(429, response.getStatus());
        }
    }

    @Test
    void ignoresForwardedForHeaderUnlessProxyIsTrusted() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = post("/login", "192.168.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.7");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertNotEquals(429, response.getStatus());
        }

        MockHttpServletRequest proxiedRequest = post("/login", "192.168.0.1");
        proxiedRequest.addHeader("X-Forwarded-For", "203.0.113.7");
        MockHttpServletResponse blockedProxyClient = new MockHttpServletResponse();
        filter.doFilter(proxiedRequest, blockedProxyClient, new MockFilterChain());
        assertEquals(429, blockedProxyClient.getStatus());

        MockHttpServletResponse directClientStillAllowed = new MockHttpServletResponse();
        filter.doFilter(post("/login", "192.168.0.1"), directClientStillAllowed, new MockFilterChain());
        assertEquals(429, directClientStillAllowed.getStatus());

        RateLimitFilter trustedFilter =
                new RateLimitFilter(3, List.of("192.168.0.1"), new FixedWindowStore(Clock.systemUTC()));
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest trustedRequest = post("/login", "192.168.0.1");
            trustedRequest.addHeader("X-Forwarded-For", "203.0.113.7");
            trustedFilter.doFilter(trustedRequest, new MockHttpServletResponse(), new MockFilterChain());
        }
        MockHttpServletRequest blockedTrustedRequest = post("/login", "192.168.0.1");
        blockedTrustedRequest.addHeader("X-Forwarded-For", "203.0.113.7");
        MockHttpServletResponse blockedTrustedResponse = new MockHttpServletResponse();
        trustedFilter.doFilter(blockedTrustedRequest, blockedTrustedResponse, new MockFilterChain());
        assertEquals(429, blockedTrustedResponse.getStatus());
    }

    @Test
    void ignoresNonProtectedRoutesAndNonPostMethods() throws Exception {
        MockHttpServletResponse getResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/login"), getResponse, new MockFilterChain());
        assertNotEquals(429, getResponse.getStatus());

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(post("/products", "9.9.9.9"), response, new MockFilterChain());
            assertNotEquals(429, response.getStatus());
        }
    }

    @Test
    void blockedResponseCarriesRetryAfterHeader() throws Exception {
        for (int i = 0; i < 3; i++) {
            filter.doFilter(post("/login", "1.2.3.4"), new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(post("/login", "1.2.3.4"), blocked, new MockFilterChain());

        assertEquals(429, blocked.getStatus());
        assertEquals("60", blocked.getHeader("Retry-After"));
    }

    @Test
    void clientErrorReportsUseAnIndependentBucketFromAuthentication() throws Exception {
        RateLimitFilter independentFilter = new RateLimitFilter(3, 2, List.of(), store);

        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            independentFilter.doFilter(post("/client-errors", "1.2.3.4"), response, new MockFilterChain());
            assertNotEquals(429, response.getStatus());
        }
        MockHttpServletResponse blockedTelemetry = new MockHttpServletResponse();
        independentFilter.doFilter(post("/client-errors", "1.2.3.4"), blockedTelemetry, new MockFilterChain());
        assertEquals(429, blockedTelemetry.getStatus());

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            independentFilter.doFilter(post("/login", "1.2.3.4"), response, new MockFilterChain());
            assertNotEquals(429, response.getStatus());
        }
        MockHttpServletResponse blockedAuthentication = new MockHttpServletResponse();
        independentFilter.doFilter(post("/login", "1.2.3.4"), blockedAuthentication, new MockFilterChain());
        assertEquals(429, blockedAuthentication.getStatus());
    }

    @Test
    void authenticatedServiceValidationUsesItsOwnQuota() throws Exception {
        final RateLimitFilter serviceFilter = new RateLimitFilter(2, 2, List.of(), store, "service-secret", 5);

        for (int i = 0; i < 5; i++) {
            final MockHttpServletRequest request = post("/validate-token", "10.0.0.5");
            request.addHeader(RateLimitFilter.INTERNAL_SERVICE_TOKEN_HEADER, "service-secret");
            final MockHttpServletResponse response = new MockHttpServletResponse();
            serviceFilter.doFilter(request, response, new MockFilterChain());
            assertNotEquals(429, response.getStatus());
        }

        final MockHttpServletRequest blockedRequest = post("/validate-token", "10.0.0.5");
        blockedRequest.addHeader(RateLimitFilter.INTERNAL_SERVICE_TOKEN_HEADER, "service-secret");
        final MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        serviceFilter.doFilter(blockedRequest, blockedResponse, new MockFilterChain());
        assertEquals(429, blockedResponse.getStatus());

        for (int i = 0; i < 2; i++) {
            final MockHttpServletResponse response = new MockHttpServletResponse();
            serviceFilter.doFilter(post("/login", "10.0.0.5"), response, new MockFilterChain());
            assertNotEquals(429, response.getStatus());
        }
    }

    @Test
    void spoofedServiceHeaderRemainsInThePublicQuota() throws Exception {
        final RateLimitFilter serviceFilter = new RateLimitFilter(2, 2, List.of(), store, "service-secret", 5);

        for (int i = 0; i < 2; i++) {
            final MockHttpServletRequest request = post("/validate-token", "10.0.0.6");
            request.addHeader(RateLimitFilter.INTERNAL_SERVICE_TOKEN_HEADER, "wrong-secret");
            serviceFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        final MockHttpServletRequest blockedRequest = post("/validate-token", "10.0.0.6");
        blockedRequest.addHeader(RateLimitFilter.INTERNAL_SERVICE_TOKEN_HEADER, "wrong-secret");
        final MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        serviceFilter.doFilter(blockedRequest, blockedResponse, new MockFilterChain());
        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void windowResetsAfterTheFixedWindowElapses() throws Exception {
        MutableClock clock = new MutableClock(0L);
        RateLimitFilter clockedFilter = new RateLimitFilter(3, List.of(), new FixedWindowStore(clock));

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            clockedFilter.doFilter(post("/login", "7.7.7.7"), response, new MockFilterChain());
            assertNotEquals(429, response.getStatus());
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        clockedFilter.doFilter(post("/login", "7.7.7.7"), blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());

        // After 60s elapse the same client gets a fresh window and is allowed again.
        clock.advance(61_000L);
        MockHttpServletResponse afterReset = new MockHttpServletResponse();
        clockedFilter.doFilter(post("/login", "7.7.7.7"), afterReset, new MockFilterChain());
        assertNotEquals(429, afterReset.getStatus());
    }

    private static final class MutableClock extends Clock {
        private long millis;

        MutableClock(long millis) {
            this.millis = millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        void advance(long ms) {
            this.millis += ms;
        }
    }

    private static final class FixedWindowStore implements com.saas.directory.service.RateLimitStore {
        private static final long WINDOW_MILLIS = 60_000L;
        private final Clock clock;
        private final Map<String, Window> windows = new HashMap<>();

        private FixedWindowStore(Clock clock) {
            this.clock = clock;
        }

        @Override
        public boolean tryAcquire(String clientKey, int maxRequestsPerWindow) {
            long now = clock.millis();
            Window current = windows.get(clientKey);
            if (current == null || now - current.start >= WINDOW_MILLIS) {
                windows.put(clientKey, new Window(now, 1));
                return true;
            }
            if (current.count >= maxRequestsPerWindow) {
                return false;
            }
            windows.put(clientKey, new Window(current.start, current.count + 1));
            return true;
        }

        private record Window(long start, int count) {}
    }
}
