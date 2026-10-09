package com.portcelana.natiart.model;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(indexes = @Index(name = "ix_guest_claim_email_cutoff", columnList = "email,cutoff"))
public class GuestOrderClaim {
    @Id
    private String id;

    @Column(nullable = false, length = 36)
    private String accountId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false)
    private Instant cutoff;

    protected GuestOrderClaim() {}

    public GuestOrderClaim(String id, String accountId, String email, Instant cutoff) {
        this.id = id;
        this.accountId = accountId;
        this.email = email;
        this.cutoff = cutoff;
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCutoff() {
        return cutoff;
    }
}
