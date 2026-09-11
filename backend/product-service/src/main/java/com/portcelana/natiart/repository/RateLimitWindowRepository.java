package com.portcelana.natiart.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.RateLimitWindow;

@Repository
public interface RateLimitWindowRepository extends JpaRepository<RateLimitWindow, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM RateLimitWindow w WHERE w.clientKey = :clientKey")
    Optional<RateLimitWindow> findByClientKeyForUpdate(@Param("clientKey") String clientKey);

    @Modifying
    @Query("DELETE FROM RateLimitWindow w WHERE w.windowStart < :cutoff")
    int deleteOlderThan(@Param("cutoff") long cutoff);
}
