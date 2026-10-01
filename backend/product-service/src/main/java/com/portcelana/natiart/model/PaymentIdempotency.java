package com.portcelana.natiart.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Durable reservation that closes the check-then-charge race for payment creation. */
@Entity
@Table(
        indexes = @Index(name = "ix_payment_idempotency_order_id", columnList = "order_id"),
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_payment_idempotency_owner_key",
                    columnNames = {"owner_external_id", "idempotency_key"}),
            @UniqueConstraint(
                    name = "uk_payment_idempotency_owner_order",
                    columnNames = {"owner_external_id", "order_id"})
        })
public class PaymentIdempotency {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 128)
    private String ownerExternalId;

    @Column(nullable = false, length = 64)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    // Nullable for order-less charges and reservations created before this link existed.
    @Column(length = 36)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentIdempotencyStatus status;

    @Column(length = 128)
    private String providerPaymentId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected PaymentIdempotency() {}

    public PaymentIdempotency(String ownerExternalId, String idempotencyKey, String requestFingerprint) {
        this(ownerExternalId, idempotencyKey, requestFingerprint, null);
    }

    public PaymentIdempotency(
            String ownerExternalId, String idempotencyKey, String requestFingerprint, String orderId) {
        this.ownerExternalId = ownerExternalId;
        this.idempotencyKey = idempotencyKey;
        this.orderId = orderId;
        this.requestFingerprint = requestFingerprint;
        this.status = PaymentIdempotencyStatus.IN_PROGRESS;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getOwnerExternalId() {
        return ownerExternalId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public String getOrderId() {
        return orderId;
    }

    public PaymentIdempotencyStatus getStatus() {
        return status;
    }

    public PaymentIdempotency setStatus(PaymentIdempotencyStatus status) {
        this.status = status;
        return this;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public PaymentIdempotency setProviderPaymentId(String providerPaymentId) {
        this.providerPaymentId = providerPaymentId;
        return this;
    }
}
