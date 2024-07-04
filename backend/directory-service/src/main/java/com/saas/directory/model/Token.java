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
    @Column(nullable = false, unique = true, length = 1024)
    private String token;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenType tokenType;
    @Column(nullable = false)
    private Instant expiry;
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    protected Token() {
        // FOR JPA
    }

    public Token(String token, User user, TokenType tokenType, Instant expiry) {
        this.id = UUID.randomUUID().toString();
        this.token = token;
        this.user = user;
        this.tokenType = tokenType;
        this.expiry = expiry;
    }

    public String getId() {
        return id;
    }

    public String getToken() {
        return token;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return Objects.equals(id, token.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
