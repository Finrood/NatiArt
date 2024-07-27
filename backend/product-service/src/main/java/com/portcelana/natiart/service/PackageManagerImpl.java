package com.portcelana.natiart.service;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.PackageDto;
import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.repository.PackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PackageManagerImpl implements PackageManager {
    private final PackageRepository packageRepository;

    public PackageManagerImpl(PackageRepository packageRepository) {
        this.packageRepository = packageRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Package> getPackage(String packageId) {
        return packageRepository.findById(packageId);
    }

    @Override
    @Transactional(readOnly = true)
    public Package getPackageOrDie(String packageId) {
        return getPackage(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package with id " + packageId + " not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Package> getPackages() {
        return packageRepository.findAll();
    }

    @Override
    @Transactional
    public Package createPackage(PackageDto packageDto) {
        final Package pack = new Package(
                packageDto.getLabel(),
                packageDto.getHeight(),
                packageDto.getWidth(),
                packageDto.getDepth()
        );
        return packageRepository.save(pack);
    }

    @Override
    @Transactional
    public Package updatePackage(PackageDto packageDto) {
        final Package pack = getPackageOrDie(packageDto.getId());
        pack.setLabel(packageDto.getLabel())
                .setHeight(packageDto.getHeight())
                .setWidth(packageDto.getWidth())
                .setDepth(packageDto.getDepth());
        return packageRepository.save(pack);
    }

    @Override
    @Transactional
    public void deletePackage(String packageId) {
        final Package pack = getPackageOrDie(packageId);
        if (!pack.getProducts().isEmpty()) {
            throw new IllegalArgumentException("Package with label [" + pack.getLabel() + "] contains products.");
        }
        packageRepository.delete(pack);
    }
}
