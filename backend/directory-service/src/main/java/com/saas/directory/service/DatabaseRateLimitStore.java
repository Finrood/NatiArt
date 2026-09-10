package com.saas.directory.service;

import java.time.Clock;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saas.directory.model.RateLimitWindow;
import com.saas.directory.repository.RateLimitWindowRepository;

@Service
public class DatabaseRateLimitStore implements RateLimitStore {
    public static final long WINDOW_MILLIS = 60_000L;

    private final RateLimitWindowRepository repository;
    private final Clock clock;

    @Autowired
    public DatabaseRateLimitStore(RateLimitWindowRepository repository) {
        this(repository, Clock.systemUTC());
    }

    DatabaseRateLimitStore(RateLimitWindowRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * A row lock makes increments shared between application instances. A
     * concurrent first request can race the insert, so the filter retries that
     * transaction after the unique-key winner has committed.
     */
    @Override
    @Transactional
    public boolean tryAcquire(String clientKey, int maxRequestsPerWindow) {
        final long now = clock.millis();
        final Optional<RateLimitWindow> existing = repository.findByClientKeyForUpdate(clientKey);
        if (existing.isEmpty()) {
            repository.save(new RateLimitWindow(clientKey, now, 1));
            return true;
        }

        final RateLimitWindow window = existing.get();
        if (now - window.getWindowStart() >= WINDOW_MILLIS) {
            window.reset(now);
            return true;
        }
        if (window.getRequestCount() >= maxRequestsPerWindow) {
            return false;
        }
        window.increment();
        return true;
    }

    @Scheduled(fixedDelayString = "${saas.security.rate-limit.cleanup-delay-millis:3600000}")
    @Transactional
    public void deleteExpiredWindows() {
        repository.deleteOlderThan(clock.millis() - WINDOW_MILLIS);
    }
}
