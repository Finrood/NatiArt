package com.saas.directory.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

/** Mailbox proof grants a short read-only capability, separate from checkout and account credentials. */
@Entity
@Table(indexes = @Index(name = "ix_guest_order_session_expiry", columnList = "expiresAt"))
public class GuestOrderSession {
    @Id
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenDigest;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false)
    private Instant cutoff;

    @Column(nullable = false)
    private Instant expiresAt;

    protected GuestOrderSession() {}

    public GuestOrderSession(String digest, String email, Instant cutoff) {
        id = UUID.randomUUID().toString();
        tokenDigest = digest;
        this.email = email;
        this.cutoff = cutoff;
        expiresAt = Instant.now().plusSeconds(86400);
    }

    public String getEmail() {
        return email;
    }

    public Instant getCutoff() {
        return cutoff;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
