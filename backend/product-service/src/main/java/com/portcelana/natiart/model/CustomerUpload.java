package com.portcelana.natiart.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A customer-owned artwork object uploaded for a future order.
 *
 * <p>The generated identifier is the only value accepted by the order API;
 * the storage URI is never taken from a client request.</p>
 */
@Entity
@Table(name = "customer_upload")
public class CustomerUpload {
    @Id
    private final String id;

    @Column(nullable = false, length = 128)
    private String ownerExternalId;

    @Column(nullable = false, length = 512)
    private String storageUri;

    @Column(nullable = false, length = 64)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant consumedAt;

    protected CustomerUpload() {
        this.id = UUID.randomUUID().toString();
    }

    public CustomerUpload(String ownerExternalId, String storageUri, String contentType, long size) {
        this(UUID.randomUUID().toString(), ownerExternalId, storageUri, contentType, size);
    }

    public CustomerUpload(String id, String ownerExternalId, String storageUri, String contentType, long size) {
        this.id = id;
        this.ownerExternalId = ownerExternalId;
        this.storageUri = storageUri;
        this.contentType = contentType;
        this.size = size;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getOwnerExternalId() {
        return ownerExternalId;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public CustomerUpload setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
        return this;
    }
}
