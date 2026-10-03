package com.portcelana.natiart.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.model.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByOrderIdAndOwnerExternalId(String orderId, String ownerExternalId);

    @Query("SELECT p.orderId FROM Payment p WHERE p.id = :id")
    Optional<String> findOrderIdById(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") String id);

    @Query("SELECT p.id FROM Payment p WHERE (p.providerStatus IS NULL OR p.providerStatus IN :statuses)"
            + " AND (p.nextReconciliationAt IS NULL OR p.nextReconciliationAt <= :now)"
            + " ORDER BY COALESCE(p.nextReconciliationAt, p.createdAt, :oldest), p.id")
    List<String> findForReconciliation(
            @Param("statuses") List<String> statuses,
            @Param("now") Instant now,
            @Param("oldest") Instant oldest,
            Pageable pageable);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.nextReconciliationAt = :next WHERE p.id = :id"
            + " AND (p.nextReconciliationAt IS NULL OR p.nextReconciliationAt <= :now)")
    int scheduleReconciliation(@Param("id") String id, @Param("now") Instant now, @Param("next") Instant next);
}
