package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.model.TokenValidationCacheEntry;
import com.portcelana.natiart.repository.TokenValidationCacheRepository;

class DatabaseTokenValidationCacheTest {
    private static final long NOW = 1_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    private String tokenWithExpiration(long expirationMillis) {
        final long expirationSeconds = expirationMillis / 1_000L;
        final String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(("{\"exp\":" + expirationSeconds + "}").getBytes(StandardCharsets.UTF_8));
        return "e30." + payload + ".signature";
    }

    @Test
    void storesOnlyDigestAndReadsEntriesSharedBySeparateCacheInstances() throws Exception {
        final TokenValidationCacheRepository repository = mock(TokenValidationCacheRepository.class);
        final ObjectMapper mapper = new ObjectMapper();
        final AuthenticationResponseDto response =
                mapper.readValue("{\"authenticated\":true,\"name\":\"customer\"}", AuthenticationResponseDto.class);
        final String token = tokenWithExpiration(NOW + 60_000L);
        final String digest = DatabaseTokenValidationCache.digest(token);
        final TokenValidationCacheEntry entry =
                new TokenValidationCacheEntry(digest, mapper.writeValueAsString(response), NOW + 30_000L);
        when(repository.findValid(eq(digest), eq(NOW))).thenReturn(Optional.of(entry));

        final DatabaseTokenValidationCache first =
                new DatabaseTokenValidationCache(repository, mapper, 30_000L, 10, CLOCK);
        final DatabaseTokenValidationCache second =
                new DatabaseTokenValidationCache(repository, mapper, 30_000L, 10, CLOCK);

        assertTrue(first.get(token).isPresent());
        assertTrue(second.get(token).isPresent());
        assertFalse(entry.getTokenDigest().contains(token));
    }

    @Test
    void treatsExpiredEntriesAsMissesAndPurgesMalformedResponses() throws Exception {
        final TokenValidationCacheRepository repository = mock(TokenValidationCacheRepository.class);
        final ObjectMapper mapper = new ObjectMapper();
        final DatabaseTokenValidationCache cache =
                new DatabaseTokenValidationCache(repository, mapper, 30_000L, 10, CLOCK);

        when(repository.findValid(eq(DatabaseTokenValidationCache.digest("expired")), eq(NOW)))
                .thenReturn(Optional.empty());
        assertTrue(cache.get("expired").isEmpty());

        final String malformedToken = tokenWithExpiration(NOW + 60_000L);
        final String digest = DatabaseTokenValidationCache.digest(malformedToken);
        when(repository.findValid(eq(digest), eq(NOW)))
                .thenReturn(Optional.of(new TokenValidationCacheEntry(digest, "not-json", NOW + 1_000L)));
        assertTrue(cache.get(malformedToken).isEmpty());
        verify(repository).deleteById(digest);
    }

    @Test
    void capsEntriesAtSignedExpirationAndRejectsStaleRowsAfterExpiration() throws Exception {
        final TokenValidationCacheRepository repository = mock(TokenValidationCacheRepository.class);
        final ObjectMapper mapper = new ObjectMapper();
        final AuthenticationResponseDto response =
                mapper.readValue("{\"authenticated\":true,\"name\":\"customer\"}", AuthenticationResponseDto.class);
        final String token = tokenWithExpiration(NOW + 1_000L);
        final String digest = DatabaseTokenValidationCache.digest(token);
        final AtomicReference<TokenValidationCacheEntry> stored = new AtomicReference<>();
        final AtomicReference<Instant> currentTime = new AtomicReference<>(Instant.ofEpochMilli(NOW));
        final Clock advancingClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return currentTime.get();
            }
        };
        when(repository.count()).thenReturn(0L);
        when(repository.findById(digest)).thenReturn(Optional.empty());
        when(repository.save(any(TokenValidationCacheEntry.class))).thenAnswer(invocation -> {
            final TokenValidationCacheEntry entry = invocation.getArgument(0);
            stored.set(entry);
            return entry;
        });
        when(repository.findValid(eq(digest), anyLong())).thenAnswer(invocation -> Optional.of(stored.get()));

        final DatabaseTokenValidationCache cache =
                new DatabaseTokenValidationCache(repository, mapper, 30_000L, 10, advancingClock);
        cache.put(token, response);

        assertEquals(NOW + 1_000L, stored.get().getExpiresAt());
        currentTime.set(Instant.ofEpochMilli(NOW + 1_001L));

        assertTrue(cache.get(token).isEmpty());
        verify(repository).deleteById(digest);
    }

    @Test
    void doesNotCacheTokensWithoutUsableFutureExpiration() throws Exception {
        final TokenValidationCacheRepository repository = mock(TokenValidationCacheRepository.class);
        final ObjectMapper mapper = new ObjectMapper();
        final AuthenticationResponseDto response =
                mapper.readValue("{\"authenticated\":true,\"name\":\"customer\"}", AuthenticationResponseDto.class);
        final DatabaseTokenValidationCache cache =
                new DatabaseTokenValidationCache(repository, mapper, 30_000L, 10, CLOCK);

        cache.put("not-a-jwt", response);

        verify(repository, org.mockito.Mockito.never()).save(any(TokenValidationCacheEntry.class));
    }

    @Test
    void rejectsInvalidBounds() {
        final TokenValidationCacheRepository repository = mock(TokenValidationCacheRepository.class);
        final ObjectMapper mapper = new ObjectMapper();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseTokenValidationCache(repository, mapper, 0, 10, CLOCK));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseTokenValidationCache(repository, mapper, 1, 0, CLOCK));
    }
}
