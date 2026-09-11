package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.portcelana.natiart.service.RateLimitStore;

class ShippingRateLimitFilterTest {
    private FixedWindowStore store;
    private ShippingRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        store = new FixedWindowStore(Clock.systemUTC());
        filter = new ShippingRateLimitFilter(2, List.of(), store);
    }

    @Test
    void limitsOnlyPostShippingEstimateRequests() throws Exception {
        for (int i = 0; i < 2; i++) {
            final MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("POST", "/shipping/estimate", "192.0.2.10"), response, new MockFilterChain());
            assertNotEquals(429, response.getStatus());
        }

        final MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/shipping/estimate", "192.0.2.10"), blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());

        final MockHttpServletResponse getResponse = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/shipping/estimate", "192.0.2.10"), getResponse, new MockFilterChain());
        assertNotEquals(429, getResponse.getStatus());
    }

    @Test
    void ignoresForwardedForFromUntrustedPeer() throws Exception {
        for (int i = 0; i < 2; i++) {
            final MockHttpServletRequest request = request("POST", "/shipping/estimate", "192.0.2.10");
            request.addHeader("X-Forwarded-For", "198.51.100.7");
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        final MockHttpServletRequest blockedRequest = request("POST", "/shipping/estimate", "192.0.2.10");
        blockedRequest.addHeader("X-Forwarded-For", "198.51.100.8");
        final MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(blockedRequest, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    @Test
    void usesOnlyNormalizedForwardedIpFromTrustedPeer() throws Exception {
        final ShippingRateLimitFilter trustedFilter =
                new ShippingRateLimitFilter(2, List.of("192.0.2.10"), new FixedWindowStore(Clock.systemUTC()));
        for (int i = 0; i < 2; i++) {
            final MockHttpServletRequest request = request("POST", "/shipping/estimate", "192.0.2.10");
            request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.1");
            trustedFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        final MockHttpServletRequest blockedRequest = request("POST", "/shipping/estimate", "192.0.2.10");
        blockedRequest.addHeader("X-Forwarded-For", "198.51.100.7");
        final MockHttpServletResponse blocked = new MockHttpServletResponse();
        trustedFilter.doFilter(blockedRequest, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    @Test
    void invalidForwardedValueFallsBackToTheTrustedProxyAddress() throws Exception {
        final ShippingRateLimitFilter trustedFilter =
                new ShippingRateLimitFilter(1, List.of("192.0.2.10"), new FixedWindowStore(Clock.systemUTC()));
        final MockHttpServletRequest first = request("POST", "/shipping/estimate", "192.0.2.10");
        first.addHeader("X-Forwarded-For", "not-an-ip");
        trustedFilter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        final MockHttpServletResponse blocked = new MockHttpServletResponse();
        trustedFilter.doFilter(request("POST", "/shipping/estimate", "192.0.2.10"), blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    @Test
    void normalizesIpv6WithoutAllowingOversizedKeys() {
        assertEquals("2001:db8:0:0:0:0:0:1", ShippingRateLimitFilter.normalizeIp("2001:db8::1"));
        assertEquals("unknown", ShippingRateLimitFilter.normalizeIp("x".repeat(129)));
        assertEquals("unknown", ShippingRateLimitFilter.normalizeIp("hostname.example"));
    }

    private MockHttpServletRequest request(String method, String uri, String remoteAddr) {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static final class FixedWindowStore implements RateLimitStore {
        private static final long WINDOW_MILLIS = 60_000L;
        private final Clock clock;
        private final Map<String, Window> windows = new HashMap<>();

        private FixedWindowStore(Clock clock) {
            this.clock = clock;
        }

        @Override
        public boolean tryAcquire(String clientKey, int maxRequestsPerWindow) {
            final long now = clock.millis();
            final Window current = windows.get(clientKey);
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
