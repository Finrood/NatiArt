package com.portcelana.natiart.configuration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.portcelana.natiart.service.DatabaseRateLimitStore;
import com.portcelana.natiart.service.RateLimitStore;

@Component
@ConditionalOnBean(RateLimitStore.class)
public class ShippingRateLimitFilter extends OncePerRequestFilter {
    private static final String SHIPPING_ESTIMATE_ROUTE = "/shipping/estimate";

    private final int maxRequestsPerWindow;
    private final List<String> trustedProxyAddresses;
    private final RateLimitStore rateLimitStore;

    public ShippingRateLimitFilter(
            @Value("${nati.security.rate-limit.max-requests-per-minute:10}") int maxRequestsPerWindow,
            @Value("${nati.security.rate-limit.trusted-proxies:}") List<String> trustedProxyAddresses,
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
        return !HttpMethod.POST.matches(request.getMethod())
                || !SHIPPING_ESTIMATE_ROUTE.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final boolean allowed;
        try {
            allowed = tryAcquireWithRetry(clientIp(request));
        } catch (RuntimeException exception) {
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
        final String remoteAddress = normalizeIp(request.getRemoteAddr());
        if (trustedProxyAddresses.contains(request.getRemoteAddr())) {
            final String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                final String forwardedAddress = normalizeIp(forwarded.split(",")[0].trim());
                if (!"unknown".equals(forwardedAddress)) {
                    return forwardedAddress;
                }
            }
        }
        return remoteAddress;
    }

    static String normalizeIp(String candidate) {
        if (candidate == null || candidate.length() > 128 || !isIpLiteral(candidate)) {
            return "unknown";
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (UnknownHostException exception) {
            return "unknown";
        }
    }

    private static boolean isIpLiteral(String candidate) {
        return candidate.matches("[0-9a-fA-F:.]+")
                && (!candidate.contains(".") || candidate.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"));
    }
}
