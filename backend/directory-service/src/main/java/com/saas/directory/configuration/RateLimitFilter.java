package com.saas.directory.configuration;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.saas.directory.service.DatabaseRateLimitStore;
import com.saas.directory.service.RateLimitStore;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final List<String> PROTECTED_ROUTES =
            List.of("/login", "/register-user", "/register-ghost-user", "/validate-token", "/refresh-token");
    private final int maxRequestsPerWindow;
    private final List<String> trustedProxyAddresses;
    private final RateLimitStore rateLimitStore;

    @Autowired
    public RateLimitFilter(
            @Value("${saas.security.rate-limit.max-requests-per-minute:10}") int maxRequestsPerWindow,
            @Value("${saas.security.rate-limit.trusted-proxies:}") List<String> trustedProxyAddresses,
            RateLimitStore rateLimitStore) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.trustedProxyAddresses = trustedProxyAddresses.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        this.rateLimitStore = rateLimitStore;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        return PROTECTED_ROUTES.stream().noneMatch(request.getRequestURI()::endsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final String clientKey = clientIp(request);
        final boolean allowed;
        try {
            allowed = tryAcquireWithRetry(clientKey);
        } catch (RuntimeException exception) {
            // A limiter that cannot reach its shared store must not silently
            // become an unlimited bypass for authentication endpoints.
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Rate limit service unavailable");
            return;
        }
        if (!allowed) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(DatabaseRateLimitStore.WINDOW_MILLIS / 1000));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean tryAcquireWithRetry(String clientKey) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return rateLimitStore.tryAcquire(clientKey, maxRequestsPerWindow);
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
                if (attempt == 2) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("Rate limit store retry loop terminated unexpectedly");
    }

    private String clientIp(HttpServletRequest request) {
        if (trustedProxyAddresses.contains(request.getRemoteAddr())) {
            final String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
