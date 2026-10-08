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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.portcelana.natiart.service.DatabaseRateLimitStore;
import com.portcelana.natiart.service.RateLimitStore;

@Component
public class ShippingRateLimitFilter extends OncePerRequestFilter {
    private static final String SHIPPING_ESTIMATE_ROUTE = "/shipping/estimate";
    private static final String SHIPPING_QUOTE_ROUTE = "/shipping/quote";
    private static final String SHIPPING_BASKET_ESTIMATE_ROUTE = "/shipping/basket-estimate";

    private final int maxRequestsPerWindow;
    private final int maxProviderRequestsPerWindow;
    private final List<String> trustedProxyAddresses;
    private final RateLimitStore rateLimitStore;

    public ShippingRateLimitFilter(
            @Value("${nati.security.rate-limit.max-requests-per-minute:10}") int maxRequestsPerWindow,
            @Value("${natiart.shipping.max-requests-per-minute:60}") int maxProviderRequestsPerWindow,
            @Value("${nati.security.rate-limit.trusted-proxies:}") List<String> trustedProxyAddresses,
            RateLimitStore rateLimitStore) {
        if (maxRequestsPerWindow < 1 || maxProviderRequestsPerWindow < 1) {
            throw new IllegalArgumentException("Shipping request limits must be positive");
        }
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.maxProviderRequestsPerWindow = maxProviderRequestsPerWindow;
        this.trustedProxyAddresses = trustedProxyAddresses.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ShippingRateLimitFilter::normalizeIp)
                .filter(s -> !"unknown".equals(s))
                .toList();
        this.rateLimitStore = rateLimitStore;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !(SHIPPING_ESTIMATE_ROUTE.equals(request.getRequestURI())
                        || SHIPPING_QUOTE_ROUTE.equals(request.getRequestURI())
                        || SHIPPING_BASKET_ESTIMATE_ROUTE.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final boolean allowed;
        try {
            // Shared across instances and all shipping endpoints; reserve headroom for
            // the shipping client's bounded retry and adjacent fixed windows.
            allowed = tryAcquireWithRetry("shipping:client:" + clientIp(request), maxRequestsPerWindow)
                    && tryAcquireWithRetry("shipping:provider", maxProviderRequestsPerWindow);
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

    private boolean tryAcquireWithRetry(String clientKey, int limit) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return rateLimitStore.tryAcquire(clientKey, limit);
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
        if (trustedProxyAddresses.contains(remoteAddress)) {
            final String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                final String[] chain = forwarded.split(",");
                for (int index = chain.length - 1; index >= 0; index--) {
                    final String forwardedAddress = normalizeIp(chain[index].trim());
                    if (!"unknown".equals(forwardedAddress) && !trustedProxyAddresses.contains(forwardedAddress)) {
                        return forwardedAddress;
                    }
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
