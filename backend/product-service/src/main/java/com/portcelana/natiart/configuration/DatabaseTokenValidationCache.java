package com.portcelana.natiart.configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.model.TokenValidationCacheEntry;
import com.portcelana.natiart.repository.TokenValidationCacheRepository;

/** Database-backed cache shared by all product-service instances. */
@Service
public class DatabaseTokenValidationCache implements TokenValidationCache {
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final TokenValidationCacheRepository repository;
    private final ObjectMapper objectMapper;
    private final long ttlMillis;
    private final int maxEntries;
    private final Clock clock;

    public DatabaseTokenValidationCache(
            TokenValidationCacheRepository repository,
            ObjectMapper objectMapper,
            @Value("${directory.service.auth-cache.ttl-millis:30000}") long ttlMillis,
            @Value("${directory.service.auth-cache.max-entries:10000}") int maxEntries) {
        this(repository, objectMapper, ttlMillis, maxEntries, Clock.systemUTC());
    }

    DatabaseTokenValidationCache(
            TokenValidationCacheRepository repository,
            ObjectMapper objectMapper,
            long ttlMillis,
            int maxEntries,
            Clock clock) {
        if (ttlMillis < 1) {
            throw new IllegalArgumentException("Token validation cache TTL must be positive");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("Token validation cache size must be positive");
        }
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.ttlMillis = ttlMillis;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<AuthenticationResponseDto> get(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        final Optional<TokenValidationCacheEntry> entry = repository.findValid(digest(token), clock.millis());
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(entry.get().getResponseJson(), AuthenticationResponseDto.class));
        } catch (JsonProcessingException exception) {
            repository.deleteById(entry.get().getTokenDigest());
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void put(String token, AuthenticationResponseDto response) {
        if (token == null || token.isBlank() || response == null) {
            return;
        }
        final String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize token validation response", exception);
        }

        final long now = clock.millis();
        repository.deleteExpired(now);
        final String digest = digest(token);
        if (repository.count() >= maxEntries && repository.findById(digest).isEmpty()) {
            repository
                    .findFirstByOrderByExpiresAtAsc()
                    .ifPresent(entry -> repository.deleteById(entry.getTokenDigest()));
        }
        repository.save(new TokenValidationCacheEntry(digest, responseJson, now + ttlMillis));
    }

    @Scheduled(fixedDelayString = "${directory.service.auth-cache.cleanup-delay-millis:60000}")
    @Transactional
    public void deleteExpiredEntries() {
        repository.deleteExpired(clock.millis());
    }

    static String digest(String token) {
        try {
            return HEX_FORMAT.formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
