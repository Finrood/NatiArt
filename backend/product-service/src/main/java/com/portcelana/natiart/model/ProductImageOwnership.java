package com.portcelana.natiart.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

/** Durable file intent; tombstones prevent re-adoption while cleanup is retried. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_product_image_uri", columnNames = "uri"))
public class ProductImageOwnership {
    public enum State {
        STAGED,
        LIVE,
        DELETE_PENDING,
        DELETED
    }

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 2048)
    private String uri;

    @Column(nullable = false, length = 36)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private State state = State.STAGED;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant nextAttemptAt = Instant.EPOCH;

    @Column(nullable = false)
    private int cleanupAttempts;

    protected ProductImageOwnership() {}

    public ProductImageOwnership(String productId, String uri) {
        this.productId = productId;
        this.uri = uri;
    }

    public String getId() {
        return id;
    }

    public String getUri() {
        return uri;
    }

    public State getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getCleanupAttempts() {
        return cleanupAttempts;
    }

    public void makeLive() {
        state = State.LIVE;
    }

    public void requestDeletion() {
        if (state != State.DELETED) state = State.DELETE_PENDING;
    }

    public void recordAttempt(Instant now) {
        cleanupAttempts++;
        nextAttemptAt = now.plusSeconds(60);
    }

    public void deleted() {
        state = State.DELETED;
    }
}
