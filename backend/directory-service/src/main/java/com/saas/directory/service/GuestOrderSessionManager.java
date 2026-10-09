package com.saas.directory.service;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saas.directory.model.GuestOrderSession;
import com.saas.directory.repository.GuestOrderSessionRepository;

@Service
public class GuestOrderSessionManager {
    private final GuestOrderSessionRepository sessions;

    public GuestOrderSessionManager(GuestOrderSessionRepository sessions) {
        this.sessions = sessions;
    }

    public record Scope(String email, Instant cutoff, Instant expiresAt) {}

    @Transactional(readOnly = true)
    public Scope validateOrDie(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new AccessDeniedException("Order link expired");
        final GuestOrderSession session = sessions.findByTokenDigest(GuestSecrets.digest(token))
                .filter(value -> value.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new AccessDeniedException("Order link expired"));
        return new Scope(session.getEmail(), session.getCutoff(), session.getExpiresAt());
    }

    @Scheduled(fixedDelayString = "${natiart.guest.cleanup-delay-millis:3600000}")
    @Transactional
    public void cleanExpired() {
        sessions.deleteExpired(Instant.now());
    }
}
