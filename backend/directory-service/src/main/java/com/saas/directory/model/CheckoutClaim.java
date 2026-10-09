package com.saas.directory.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

/** Email proof and its durable commerce delivery are one purpose-specific record. */
@Entity
@Table(indexes = @Index(name = "ix_checkout_claim_delivery", columnList = "nextDeliveryAt"))
public class CheckoutClaim {
    @Id
    private String id;

    @Version
    private long version;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenDigest;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 8192)
    private String profileJson;

    @Column(nullable = false)
    private Instant cutoff;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant consumedAt;

    @Column(length = 36)
    private String accountId;

    private Instant deliveredAt;
    private Instant nextDeliveryAt;

    protected CheckoutClaim() {}

    public CheckoutClaim(String digest, String email, String profileJson, Instant now) {
        id = UUID.randomUUID().toString();
        tokenDigest = digest;
        this.email = email;
        this.profileJson = profileJson;
        cutoff = now;
        expiresAt = now.plusSeconds(900);
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getProfileJson() {
        return profileJson;
    }

    public Instant getCutoff() {
        return cutoff;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public void consume(String accountId, Instant now) {
        this.accountId = accountId;
        consumedAt = now;
        nextDeliveryAt = now;
    }

    public void delivered() {
        deliveredAt = Instant.now();
    }

    public void retry() {
        nextDeliveryAt = Instant.now().plusSeconds(30);
    }
}
