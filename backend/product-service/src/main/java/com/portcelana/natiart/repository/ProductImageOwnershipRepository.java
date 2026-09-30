package com.portcelana.natiart.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.portcelana.natiart.model.ProductImageOwnership;
import com.portcelana.natiart.model.ProductImageOwnership.State;

public interface ProductImageOwnershipRepository extends JpaRepository<ProductImageOwnership, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from ProductImageOwnership f where f.id = :id")
    Optional<ProductImageOwnership> findByIdForUpdate(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from ProductImageOwnership f where f.uri = :uri")
    Optional<ProductImageOwnership> findByUriForUpdate(String uri);

    @Query(
            "select f.id from ProductImageOwnership f where (f.state = :pending or (f.state = :staged and f.createdAt <= :cutoff)) and f.nextAttemptAt <= :now order by f.createdAt, f.id")
    List<String> findCleanupCandidates(State pending, State staged, Instant cutoff, Instant now, Pageable pageable);

    @Query("select count(p) from Personalization p join p.personalizationOptions option where value(option) = :uri")
    long countPersonalizationReferences(String uri);
}
