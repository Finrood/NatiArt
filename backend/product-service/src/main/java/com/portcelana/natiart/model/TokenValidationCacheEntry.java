package com.portcelana.natiart.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "token_validation_cache")
public class TokenValidationCacheEntry {
    @Id
    @Column(length = 64)
    private String tokenDigest;

    @Lob
    @Column(nullable = false)
    private String responseJson;

    @Column(nullable = false)
    private long expiresAt;

    protected TokenValidationCacheEntry() {}

    public TokenValidationCacheEntry(String tokenDigest, String responseJson, long expiresAt) {
        this.tokenDigest = tokenDigest;
        this.responseJson = responseJson;
        this.expiresAt = expiresAt;
    }

    public String getTokenDigest() {
        return tokenDigest;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public long getExpiresAt() {
        return expiresAt;
    }
}
