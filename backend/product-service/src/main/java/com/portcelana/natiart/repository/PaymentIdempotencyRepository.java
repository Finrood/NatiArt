package com.portcelana.natiart.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.PaymentIdempotency;

@Repository
public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotency, String> {
    Optional<PaymentIdempotency> findByOwnerExternalIdAndIdempotencyKey(String ownerExternalId, String idempotencyKey);
}
