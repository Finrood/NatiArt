package com.portcelana.natiart.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.portcelana.natiart.model.TokenValidationCacheEntry;

@DataJpaTest
class TokenValidationCacheRepositoryTest {
    @Autowired
    private TokenValidationCacheRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void invalidationDeletesOnlyTheChangedUsersCachedValidations() {
        repository.save(new TokenValidationCacheEntry("a".repeat(64), "user-1", "{}", 60_000));
        repository.save(new TokenValidationCacheEntry("b".repeat(64), "user-1", "{}", 60_000));
        repository.save(new TokenValidationCacheEntry("c".repeat(64), "user-2", "{}", 60_000));
        repository.flush();
        entityManager.clear();

        assertEquals(2, repository.deleteByUserId("user-1"));
        entityManager.clear();

        assertFalse(repository.existsById("a".repeat(64)));
        assertFalse(repository.existsById("b".repeat(64)));
        assertTrue(repository.existsById("c".repeat(64)));
    }
}
