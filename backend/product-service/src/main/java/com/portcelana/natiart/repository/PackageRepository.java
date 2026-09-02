package com.portcelana.natiart.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.Package;

@Repository
public interface PackageRepository extends JpaRepository<Package, String> {
    Optional<Package> findPackageByLabel(String label);
}
