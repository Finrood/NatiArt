package com.saas.directory.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

/** A checkout customer has no credentials, roles or account tokens. */
@Entity
@Table(indexes = @Index(name = "ix_guest_customer_email_created", columnList = "email,createdAt"))
public class GuestCustomer {
    @Id
    private String id;

    @Version
    private long version;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 8192)
    private String profileJson;

    @Column(length = 128)
    private String providerCustomerId;

    @Column(nullable = false)
    private Instant createdAt;

    protected GuestCustomer() {}

    public GuestCustomer(String email, String profileJson) {
        this.id = UUID.randomUUID().toString();
        this.email = email;
        this.profileJson = profileJson;
        this.createdAt = Instant.now();
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

    public String getProviderCustomerId() {
        return providerCustomerId;
    }

    public void setProviderCustomerId(String value) {
        providerCustomerId = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
