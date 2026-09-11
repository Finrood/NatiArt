package com.portcelana.natiart.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.TokenValidationCacheEntry;

@Repository
public interface TokenValidationCacheRepository extends JpaRepository<TokenValidationCacheEntry, String> {
    @Query("SELECT e FROM TokenValidationCacheEntry e WHERE e.tokenDigest = :digest AND e.expiresAt > :now")
    Optional<TokenValidationCacheEntry> findValid(@Param("digest") String digest, @Param("now") long now);

    Optional<TokenValidationCacheEntry> findFirstByOrderByExpiresAtAsc();

    @Modifying
    @Query("DELETE FROM TokenValidationCacheEntry e WHERE e.expiresAt <= :now")
    int deleteExpired(@Param("now") long now);
}
