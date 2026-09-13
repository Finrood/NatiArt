package com.portcelana.natiart.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.ShippingQuote;

@Repository
public interface ShippingQuoteRepository extends JpaRepository<ShippingQuote, String> {
    Optional<ShippingQuote> findByIdAndOwnerExternalId(String id, String ownerExternalId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT q FROM ShippingQuote q WHERE q.id = :id AND q.ownerExternalId = :ownerExternalId")
    Optional<ShippingQuote> findByIdAndOwnerExternalIdForUse(
            @Param("id") String id, @Param("ownerExternalId") String ownerExternalId);
}
