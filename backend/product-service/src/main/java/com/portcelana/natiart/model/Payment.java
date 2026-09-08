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
    @Column
    private String orderId;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    protected Payment() {}

    public Payment(String id, String ownerExternalId) {
        this(id, ownerExternalId, null);
    }

    public Payment(String id, String ownerExternalId, String orderId) {
        this.id = id;
        this.ownerExternalId = ownerExternalId;
        this.orderId = orderId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
