package com.saas.directory.repository;

import java.time.Instant;
import java.util.*;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import com.saas.directory.model.CheckoutClaim;

public interface CheckoutClaimRepository extends JpaRepository<CheckoutClaim, String> {
    Optional<CheckoutClaim> findByTokenDigest(String digest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CheckoutClaim c where c.tokenDigest = :digest")
    Optional<CheckoutClaim> findByTokenDigestForUpdate(@Param("digest") String digest);

    @Query(
            "select c.id from CheckoutClaim c where c.accountId IS NOT NULL and c.consumedAt IS NOT NULL and c.deliveredAt IS NULL and c.nextDeliveryAt <= :now order by c.nextDeliveryAt")
    List<String> findDueIds(@Param("now") Instant now, org.springframework.data.domain.Pageable page);
}
