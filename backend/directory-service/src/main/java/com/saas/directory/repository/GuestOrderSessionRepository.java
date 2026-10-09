package com.saas.directory.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.*;

import com.saas.directory.model.GuestOrderSession;

public interface GuestOrderSessionRepository extends JpaRepository<GuestOrderSession, String> {
    Optional<GuestOrderSession> findByTokenDigest(String digest);

    @Modifying
    @Query("delete from GuestOrderSession s where s.expiresAt <= :now")
    int deleteExpired(Instant now);
}
