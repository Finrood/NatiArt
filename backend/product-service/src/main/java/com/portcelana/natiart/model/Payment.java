package com.portcelana.natiart.model;

import java.time.Instant;

import jakarta.persistence.*;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class Payment {
    @Id
    private String id;

    @Column(nullable = false)
    private String ownerExternalId;

    // Set only when the charge was placed against a specific order; the value
    // was then reconciled server-side against CustomerOrder.totalAmount.
    // Unique (nullable: order-less charges stay null) so a retried POST for
    // the same order fails loud on a second insert instead of persisting a
    // duplicate ledger row -- the service dedupes before egress, this is the
    // backstop for a check-then-insert race. Applies to freshly created
    // schemas only: with ddl-auto=update Hibernate does not add unique
    // constraints to pre-existing tables, so deployments created before this
    // change need a manual migration to gain the backstop.
    @Column(unique = true)
    private String orderId;

    @Column(length = 64)
    private String idempotencyKey;

    @Column(length = 64)
    private String providerStatus;

    private Instant providerUpdatedAt;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    protected Payment() {}

    public Payment(String id, String ownerExternalId) {
        this(id, ownerExternalId, null);
    }

    public Payment(String id, String ownerExternalId, String orderId) {
        this(id, ownerExternalId, orderId, null);
    }

    public Payment(String id, String ownerExternalId, String orderId, String idempotencyKey) {
        this.id = id;
        this.ownerExternalId = ownerExternalId;
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
    }

    public String getId() {
        return id;
    }

    public String getOwnerExternalId() {
        return ownerExternalId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public Payment setProviderStatus(String providerStatus) {
        this.providerStatus = providerStatus;
        return this;
    }

    public Instant getProviderUpdatedAt() {
        return providerUpdatedAt;
    }

    public Payment setProviderUpdatedAt(Instant providerUpdatedAt) {
        this.providerUpdatedAt = providerUpdatedAt;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
