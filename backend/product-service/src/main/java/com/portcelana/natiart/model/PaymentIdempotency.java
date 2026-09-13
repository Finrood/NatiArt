package com.portcelana.natiart.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Durable reservation that closes the check-then-charge race for payment creation. */
@Entity
@Table(
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

    @Column(length = 128)
    private String orderId;

    @Column(nullable = false, length = 64)
    private String requestFingerprint;

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
        this(ownerExternalId, idempotencyKey, null, requestFingerprint);
    }

    public PaymentIdempotency(
            String ownerExternalId, String idempotencyKey, String orderId, String requestFingerprint) {
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

    public String getOrderId() {
        return orderId;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
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
