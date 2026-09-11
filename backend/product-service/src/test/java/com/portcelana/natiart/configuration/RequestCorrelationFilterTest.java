package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesAndEchoesCorrelationIdWhileRequestIsInFlight() throws Exception {
        final RequestCorrelationFilter filter = new RequestCorrelationFilter();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                assertNotNull(MDC.get(RequestCorrelationFilter.MDC_KEY));
                assertEquals(
                        MDC.get(RequestCorrelationFilter.MDC_KEY),
                        ((MockHttpServletResponse) response).getHeader(RequestCorrelationFilter.HEADER_NAME));
            }
        };

        filter.doFilter(new MockHttpServletRequest("GET", "/products"), response, chain);

        assertNull(MDC.get(RequestCorrelationFilter.MDC_KEY));
        assertNotNull(response.getHeader(RequestCorrelationFilter.HEADER_NAME));
    }

    @Test
    void preservesSafeIncomingIdAndReplacesUnsafeIncomingId() throws Exception {
        final RequestCorrelationFilter filter = new RequestCorrelationFilter();
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        request.addHeader(RequestCorrelationFilter.HEADER_NAME, "checkout-42");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("checkout-42", response.getHeader(RequestCorrelationFilter.HEADER_NAME));

        final MockHttpServletRequest unsafeRequest = new MockHttpServletRequest("GET", "/products");
        unsafeRequest.addHeader(RequestCorrelationFilter.HEADER_NAME, "bad\nvalue");
        final MockHttpServletResponse unsafeResponse = new MockHttpServletResponse();
        filter.doFilter(unsafeRequest, unsafeResponse, new MockFilterChain());

        assertNotNull(unsafeResponse.getHeader(RequestCorrelationFilter.HEADER_NAME));
        assertEquals(
                36,
                unsafeResponse.getHeader(RequestCorrelationFilter.HEADER_NAME).length());
    }
}
