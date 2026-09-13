package com.portcelana.natiart.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.CustomerUpload;

@Repository
public interface CustomerUploadRepository extends JpaRepository<CustomerUpload, String> {
    /** Locks the upload while it is claimed so one artwork cannot fund two orders. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT upload FROM CustomerUpload upload WHERE upload.id = :id")
    Optional<CustomerUpload> findByIdForUpdate(@Param("id") String id);
}
