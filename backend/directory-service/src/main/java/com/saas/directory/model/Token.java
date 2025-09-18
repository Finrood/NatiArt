package com.saas.directory.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
public class Token {
    @Id
    private String id;
    @Version
    private long version;
    @Column(nullable = false, unique = true)
    private String jti;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenType tokenType;
    @Column(nullable = false)
    private Instant expiry;
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    protected Token() {
        // FOR JPA
    }

    public Token(String jti, User user, TokenType tokenType, Instant expiry) {
        this.id = UUID.randomUUID().toString();
        this.jti = jti;
        this.user = user;
        this.tokenType = tokenType;
        this.expiry = expiry;
    }

    public String getId() {
        return id;
    }

    public String getJti() {
        return jti;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public Instant getExpiry() {
        return expiry;
    }

    public User getUser() {
        return user;
    }

    public Token setUser(User user) {
        this.user = user;
        return this;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiry);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return Objects.equals(id, token.id);
    }
}
