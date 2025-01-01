package com.portcelana.natiart.configuration;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PerformanceLoggingFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceLoggingFilter.class);

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

        LOGGER.info("Request [{}] to [{}] took [{}] ms", method, requestURI, duration);
    }

    @Override
    public void destroy() {
    }
}

