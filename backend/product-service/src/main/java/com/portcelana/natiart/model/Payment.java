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

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    protected Payment() {}

    public Payment(String id, String ownerExternalId) {
        this.id = id;
        this.ownerExternalId = ownerExternalId;
    }

    public String getId() {
        return id;
    }

    public String getOwnerExternalId() {
        return ownerExternalId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
