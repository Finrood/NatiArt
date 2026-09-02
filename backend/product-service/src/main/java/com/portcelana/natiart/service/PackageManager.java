package com.portcelana.natiart.service;

import java.util.List;
import java.util.Optional;

import com.portcelana.natiart.dto.PackageDto;
import com.portcelana.natiart.model.Package;

public interface PackageManager {
    Optional<Package> getPackage(String packageId);

    Package getPackageOrDie(String packageId);

    List<Package> getPackages();

    Package createPackage(PackageDto packageDto);

    Package updatePackage(PackageDto packageDto);

    void deletePackage(String packageId);
}
