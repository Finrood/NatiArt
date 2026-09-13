package com.saas.directory.configuration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String INTERNAL_VALIDATION_BUCKET = "internal-token-validation";
    private static final List<String> PROTECTED_ROUTES =
            List.of("/login", "/register-user", "/validate-token", "/refresh-token", "/client-errors");
    private final int maxRequestsPerWindow;
    private final int clientErrorMaxRequestsPerWindow;
    private final int internalValidationMaxRequestsPerWindow;
    private final List<String> trustedProxyAddresses;
    private final RateLimitStore rateLimitStore;
    private final String internalValidationSecret;

    @Autowired
    public RateLimitFilter(
            @Value("${saas.security.rate-limit.max-requests-per-minute:10}") int maxRequestsPerWindow,
            @Value("${saas.security.rate-limit.client-error-max-requests-per-minute:10}")
                    int clientErrorMaxRequestsPerWindow,
            @Value("${saas.security.rate-limit.trusted-proxies:}") List<String> trustedProxyAddresses,
            RateLimitStore rateLimitStore,
            @Value("${saas.security.internal.validation-secret:}") String internalValidationSecret,
            @Value("${saas.security.rate-limit.internal-validation-max-requests-per-minute:600}")
                    int internalValidationMaxRequestsPerWindow) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.clientErrorMaxRequestsPerWindow = clientErrorMaxRequestsPerWindow;
        this.internalValidationMaxRequestsPerWindow = internalValidationMaxRequestsPerWindow;
        this.trustedProxyAddresses = trustedProxyAddresses.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        this.rateLimitStore = rateLimitStore;
        this.internalValidationSecret = internalValidationSecret;
    }

    RateLimitFilter(int maxRequestsPerWindow, List<String> trustedProxyAddresses, RateLimitStore rateLimitStore) {
        this(maxRequestsPerWindow, maxRequestsPerWindow, trustedProxyAddresses, rateLimitStore, "", maxRequestsPerWindow);
    }

    RateLimitFilter(int maxRequestsPerWindow, int clientErrorMaxRequestsPerWindow,
            List<String> trustedProxyAddresses, RateLimitStore rateLimitStore) {
        this(maxRequestsPerWindow, clientErrorMaxRequestsPerWindow, trustedProxyAddresses, rateLimitStore, "",
                maxRequestsPerWindow);
    }

    RateLimitFilter(int maxRequestsPerWindow, int clientErrorMaxRequestsPerWindow,
            List<String> trustedProxyAddresses, RateLimitStore rateLimitStore, String internalValidationSecret,
            int internalValidationMaxRequestsPerWindow) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.clientErrorMaxRequestsPerWindow = clientErrorMaxRequestsPerWindow;
        this.internalValidationMaxRequestsPerWindow = internalValidationMaxRequestsPerWindow;
        this.trustedProxyAddresses = trustedProxyAddresses.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        this.rateLimitStore = rateLimitStore;
        this.internalValidationSecret = internalValidationSecret;
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
        final boolean clientErrorRequest = isClientErrorRequest(request);
        final boolean internalValidationRequest = isInternalValidationRequest(request);
        final String clientKey;
        final int requestLimit;
        if (internalValidationRequest) {
            clientKey = INTERNAL_VALIDATION_BUCKET;
            requestLimit = internalValidationMaxRequestsPerWindow;
        } else {
            clientKey = clientErrorRequest ? "client-error:" + clientIp(request) : clientIp(request);
            requestLimit = clientErrorRequest ? clientErrorMaxRequestsPerWindow : maxRequestsPerWindow;
        }
        final boolean allowed;
        try {
            allowed = tryAcquireWithRetry(clientKey, requestLimit);
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

    private boolean tryAcquireWithRetry(String clientKey, int requestLimit) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return rateLimitStore.tryAcquire(clientKey, requestLimit);
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
                if (attempt == 2) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("Rate limit store retry loop terminated unexpectedly");
    }

    private boolean isClientErrorRequest(HttpServletRequest request) {
        return request.getRequestURI().endsWith("/client-errors");
    }

    private boolean isInternalValidationRequest(HttpServletRequest request) {
        if (!request.getRequestURI().endsWith("/validate-token") || internalValidationSecret == null
                || internalValidationSecret.isBlank()) {
            return false;
        }
        final String presentedSecret = request.getHeader(INTERNAL_SERVICE_TOKEN_HEADER);
        if (presentedSecret == null || presentedSecret.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                internalValidationSecret.getBytes(StandardCharsets.UTF_8), presentedSecret.getBytes(StandardCharsets.UTF_8));
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

    private static String normalizeIp(String candidate) {
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
