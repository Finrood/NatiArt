package com.portcelana.natiart.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByOrderIdAndOwnerExternalId(String orderId, String ownerExternalId);

    @Query("SELECT p FROM Payment p WHERE p.providerStatus IS NULL OR p.providerStatus IN :statuses ORDER BY p.createdAt ASC")
    List<Payment> findForReconciliation(@Param("statuses") List<String> statuses, Pageable pageable);
}
