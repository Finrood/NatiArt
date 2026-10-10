package com.portcelana.natiart.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import com.portcelana.natiart.model.OrderNotification;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM OrderNotification n WHERE n.id = :id")
    Optional<OrderNotification> findLocked(@Param("id") String id);

    @Query(
            "SELECT n.id FROM OrderNotification n WHERE n.deliveredAt IS NULL AND n.supersededAt IS NULL AND n.attempts < 8 "
                    + "AND n.nextAttemptAt <= :now AND (n.leaseUntil IS NULL OR n.leaseUntil <= :now) ORDER BY n.createdAt, n.id")
    List<String> findDueIds(@Param("now") Instant now, Pageable page);

    @Query(
            "SELECT n FROM OrderNotification n WHERE n.deliveredAt IS NULL AND n.supersededAt IS NULL AND n.attempts > 0 AND (n.leaseUntil IS NULL OR n.leaseUntil <= :now) ORDER BY n.createdAt, n.id")
    List<OrderNotification> findAttention(@Param("now") Instant now, Pageable page);

    @Query(
            "SELECT COUNT(n) FROM OrderNotification n WHERE n.deliveredAt IS NULL AND n.supersededAt IS NULL AND n.attempts > 0 AND (n.leaseUntil IS NULL OR n.leaseUntil <= :now)")
    long countAttention(@Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM OrderNotification n WHERE (n.deliveredAt < :cutoff OR n.supersededAt < :cutoff)")
    int deleteDeliveredBefore(@Param("cutoff") Instant cutoff);
}
