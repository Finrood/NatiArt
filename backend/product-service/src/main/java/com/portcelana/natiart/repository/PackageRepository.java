package com.portcelana.natiart.repository;

import com.portcelana.natiart.model.Package;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PackageRepository extends JpaRepository<Package, String>  {
    Optional<Package> findPackageByLabel(String label);
}
