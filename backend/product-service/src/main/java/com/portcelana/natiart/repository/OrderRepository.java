package com.portcelana.natiart.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<CustomerOrder, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "items")
    @Query("SELECT o FROM CustomerOrder o WHERE o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") String id);

    @Query(
            "SELECT o.id FROM CustomerOrder o WHERE o.status = :status AND o.orderDate < :cutoff AND (o.reservationNextAttemptAt IS NULL OR o.reservationNextAttemptAt <= :now) ORDER BY COALESCE(o.reservationNextAttemptAt, o.orderDate), o.id")
    List<String> findPendingOrderIdsBefore(
            @Param("status") OrderStatus status,
            @Param("cutoff") Instant cutoff,
            @Param("now") Instant now,
            Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE CustomerOrder o SET o.reservationNextAttemptAt = :next WHERE o.id = :id AND o.status = :status")
    int scheduleReservationRetry(
            @Param("id") String id, @Param("status") OrderStatus status, @Param("next") Instant next);

    long countByOwnerExternalIdAndStatus(String ownerExternalId, OrderStatus status);

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<CustomerOrder> findByOwnerExternalIdAndIdempotencyKey(String ownerExternalId, String idempotencyKey);

    @Query(
            "SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END FROM CustomerOrder customerOrder JOIN customerOrder.items item WHERE item.product = :product")
    boolean existsByProduct(@Param("product") Product product);
}
