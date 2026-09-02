package com.saas.directory.configuration;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final List<String> PROTECTED_ROUTES =
            List.of("/login", "/register-user", "/register-ghost-user", "/validate-token", "/refresh-token");
    private static final long WINDOW_MILLIS = 60_000L;
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final int maxRequestsPerWindow;
    private final Clock clock;
    private final ConcurrentHashMap<String, AtomicReference<Window>> windowsByClient = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(@Value("${saas.security.rate-limit.max-requests-per-minute:10}") int maxRequestsPerWindow) {
        this(maxRequestsPerWindow, Clock.systemUTC());
    }

    RateLimitFilter(int maxRequestsPerWindow, Clock clock) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.clock = clock;
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
        final long now = clock.millis();

        if (windowsByClient.size() > MAX_TRACKED_CLIENTS) {
            windowsByClient.entrySet().removeIf(e -> e.getValue().get().isStale(now));
        }

        final AtomicReference<Window> windowRef =
                windowsByClient.computeIfAbsent(clientKey, k -> new AtomicReference<>(new Window(now, 0)));
        final Window updated = windowRef.accumulateAndGet(
                new Window(now, 1),
                (current, incoming) -> current.isStale(incoming.start)
                        ? incoming
                        : new Window(current.start, current.count + incoming.count));

        if (updated.count > maxRequestsPerWindow) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(WINDOW_MILLIS / 1000));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        final String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Window(long start, int count) {
        boolean isStale(long now) {
            return now - start >= WINDOW_MILLIS;
        }
    }
}
