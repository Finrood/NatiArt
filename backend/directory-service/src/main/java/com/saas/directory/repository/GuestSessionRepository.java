package com.saas.directory.repository;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import com.saas.directory.model.GuestSession;

public interface GuestSessionRepository extends JpaRepository<GuestSession, String> {
    Optional<GuestSession> findByTokenDigest(String digest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GuestSession s where s.tokenDigest = :digest")
    Optional<GuestSession> findByTokenDigestForUpdate(@Param("digest") String digest);

    @Modifying
    @Query("delete from GuestSession s where s.customer.email = :email and s.customer.createdAt <= :cutoff")
    int revokeForEmail(@Param("email") String email, @Param("cutoff") Instant cutoff);

    @Modifying
    @Query("delete from GuestSession s where s.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
