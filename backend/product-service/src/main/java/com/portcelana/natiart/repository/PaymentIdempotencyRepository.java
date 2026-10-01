package com.portcelana.natiart.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.PaymentIdempotency;
import com.portcelana.natiart.model.PaymentIdempotencyStatus;

@Repository
public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotency, String> {
    Optional<PaymentIdempotency> findByOwnerExternalIdAndIdempotencyKey(String ownerExternalId, String idempotencyKey);

    List<PaymentIdempotency> findByOrderId(String orderId);

    Optional<PaymentIdempotency> findByOwnerExternalIdAndOrderId(String ownerExternalId, String orderId);

    List<PaymentIdempotency> findByOwnerExternalIdAndOrderIdIsNullAndStatusIn(
            String ownerExternalId, List<PaymentIdempotencyStatus> statuses);

    @Query(
            "SELECT p FROM PaymentIdempotency p WHERE p.status = :status AND p.updatedAt < :cutoff ORDER BY p.updatedAt ASC")
    List<PaymentIdempotency> findStaleByStatus(
            @Param("status") PaymentIdempotencyStatus status, @Param("cutoff") Instant cutoff, Pageable pageable);
}
