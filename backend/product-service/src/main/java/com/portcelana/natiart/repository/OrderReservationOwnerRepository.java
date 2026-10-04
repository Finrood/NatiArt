package com.portcelana.natiart.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.model.OrderReservationOwner;

@Repository
public interface OrderReservationOwnerRepository extends JpaRepository<OrderReservationOwner, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT owner FROM OrderReservationOwner owner WHERE owner.ownerExternalId = :ownerExternalId")
    Optional<OrderReservationOwner> findByOwnerExternalIdForUpdate(@Param("ownerExternalId") String ownerExternalId);

    /** Creates the stable lock row independently; a concurrent insert may lose on its primary key. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    default void initializeOwner(String ownerExternalId) {
        if (!existsById(ownerExternalId)) {
            saveAndFlush(new OrderReservationOwner(ownerExternalId));
        }
    }
}
