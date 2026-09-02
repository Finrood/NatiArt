package com.saas.directory.configuration;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PerformanceLoggingFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceLoggingFilter.class);
    private static final long SLOW_REQUEST_THRESHOLD_MS = 1_000L;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        final HttpServletRequest httpRequest = (HttpServletRequest) request;
        final String method = httpRequest.getMethod();
        final String requestURI = httpRequest.getRequestURI();

        final long startTime = System.currentTimeMillis();

        chain.doFilter(request, response);

        final long duration = System.currentTimeMillis() - startTime;

        if (duration >= SLOW_REQUEST_THRESHOLD_MS) {
            LOGGER.info("SLOW request [{}] to [{}] took [{}] ms", method, requestURI, duration);
        } else {
            LOGGER.debug("Request [{}] to [{}] took [{}] ms", method, requestURI, duration);
        }
    }

    @Override
    public void destroy() {
    }
}

