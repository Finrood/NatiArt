package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.model.TokenValidationCacheEntry;
import com.portcelana.natiart.repository.TokenValidationCacheRepository;

class DatabaseTokenValidationCacheTest {
    private static final long NOW = 1_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Test
    void storesOnlyDigestAndReadsEntriesSharedBySeparateCacheInstances() throws Exception {
        final TokenValidationCacheRepository repository = mock(TokenValidationCacheRepository.class);
        final ObjectMapper mapper = new ObjectMapper();
        final AuthenticationResponseDto response =
                mapper.readValue("{\"authenticated\":true,\"name\":\"customer\"}", AuthenticationResponseDto.class);
        final String digest = DatabaseTokenValidationCache.digest("secret-token");
        final TokenValidationCacheEntry entry =
                new TokenValidationCacheEntry(digest, mapper.writeValueAsString(response), NOW + 30_000L);
        when(repository.findValid(eq(digest), eq(NOW))).thenReturn(Optional.of(entry));

        final DatabaseTokenValidationCache first =
                new DatabaseTokenValidationCache(repository, mapper, 30_000L, 10, CLOCK);
        final DatabaseTokenValidationCache second =
                new DatabaseTokenValidationCache(repository, mapper, 30_000L, 10, CLOCK);

        assertTrue(first.get("secret-token").isPresent());
        assertTrue(second.get("secret-token").isPresent());
        assertFalse(entry.getTokenDigest().contains("secret-token"));
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

        final String digest = DatabaseTokenValidationCache.digest("malformed");
        when(repository.findValid(eq(digest), eq(NOW)))
                .thenReturn(Optional.of(new TokenValidationCacheEntry(digest, "not-json", NOW + 1_000L)));
        assertTrue(cache.get("malformed").isEmpty());
        verify(repository).deleteById(digest);
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
