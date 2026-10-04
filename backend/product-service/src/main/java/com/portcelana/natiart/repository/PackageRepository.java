package com.portcelana.natiart.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.Package;

@Repository
public interface PackageRepository extends JpaRepository<Package, String> {
    Page<Package> findByActiveTrue(Pageable pageable);

    Optional<Package> findPackageByLabel(String label);
}
