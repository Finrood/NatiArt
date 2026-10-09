package com.saas.directory.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(
        indexes = {
            @Index(name = "ix_guest_session_expiry", columnList = "expiresAt"),
            @Index(name = "ix_guest_session_customer", columnList = "customer_id")
        })
public class GuestSession {
    @Id
    private String id;

    @Version
    private long version;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenDigest;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean remembered;

    @ManyToOne(fetch = FetchType.EAGER)
    private GuestCustomer customer;

    @Column(length = 8192)
    private String draftJson;

    @Column(length = 65536)
    private String attemptJson;

    protected GuestSession() {}

    public GuestSession(String tokenDigest, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.tokenDigest = tokenDigest;
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public String getTokenDigest() {
        return tokenDigest;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRemembered() {
        return remembered;
    }

    public GuestCustomer getCustomer() {
        return customer;
    }

    public String getDraftJson() {
        return draftJson;
    }

    public String getAttemptJson() {
        return attemptJson;
    }

    public void setCustomer(GuestCustomer customer) {
        this.customer = customer;
    }

    public void setDraftJson(String value) {
        draftJson = value;
    }

    public void setAttemptJson(String value) {
        attemptJson = value;
    }

    public void remember(boolean value, Instant now) {
        remembered = value;
        expiresAt = now.plusSeconds(value ? 30L * 86400 : 86400);
    }
}
