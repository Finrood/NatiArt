package com.saas.directory.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import com.saas.directory.model.GuestCustomer;

public interface GuestCustomerRepository extends JpaRepository<GuestCustomer, String> {
    Optional<GuestCustomer> findFirstByEmailOrderByCreatedAtDesc(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from GuestCustomer c where c.id = :id")
    Optional<GuestCustomer> findByIdForUpdate(@Param("id") String id);
}
