package com.portcelana.natiart.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.portcelana.natiart.configuration.TokenValidationCache;

/** Internal directory callback; its shared secret is independent of customer JWTs. */
@RestController
public class AuthCacheInvalidationController {
    public static final String SECRET_HEADER = "X-NatiArt-Internal-Secret";

    private final TokenValidationCache validationCache;
    private final String sharedSecret;

    public AuthCacheInvalidationController(
            TokenValidationCache validationCache,
            @Value("${natiart.auth-cache.invalidation-secret:}") String sharedSecret) {
        this.validationCache = validationCache;
        this.sharedSecret = sharedSecret;
    }

    @PostMapping("/internal/auth-cache/users/{userId}/invalidate")
    public ResponseEntity<Void> invalidateUser(
            @PathVariable String userId,
            @RequestHeader(value = SECRET_HEADER, required = false) String suppliedSecret) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (suppliedSecret == null
                || !MessageDigest.isEqual(
                        sharedSecret.getBytes(StandardCharsets.UTF_8),
                        suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        validationCache.evictUser(userId);
        return ResponseEntity.noContent().build();
    }
}
