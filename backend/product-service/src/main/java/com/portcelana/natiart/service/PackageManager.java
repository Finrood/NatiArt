package com.portcelana.natiart.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

import com.portcelana.natiart.dto.PackageDto;
import com.portcelana.natiart.model.Package;

public interface PackageManager {
    Optional<Package> getPackage(String packageId);

    Package getPackageOrDie(String packageId);

    List<Package> getPackages(Pageable pageable);

    Package createPackage(PackageDto packageDto);

    Package updatePackage(PackageDto packageDto);

    void deletePackage(String packageId);
}
