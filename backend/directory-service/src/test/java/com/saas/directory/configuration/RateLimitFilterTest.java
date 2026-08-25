package com.saas.directory.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(3);
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
    void honorsForwardedForHeader() throws Exception {
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
        assertNotEquals(429, directClientStillAllowed.getStatus());
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
}
