package com.portcelana.natiart.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.PackageDto;
import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.repository.PackageRepository;
import com.portcelana.natiart.repository.ProductRepository;

@Service
public class PackageManagerImpl implements PackageManager {
    private final PackageRepository packageRepository;
    private final ProductRepository productRepository;

    public PackageManagerImpl(PackageRepository packageRepository, ProductRepository productRepository) {
        this.packageRepository = packageRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Package> getPackage(String packageId) {
        if (packageId == null) {
            return Optional.empty();
        }
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
        final String label = requireNonBlankLabel(packageDto.getLabel());
        requirePositiveDimension(packageDto.getHeight(), "height");
        requirePositiveDimension(packageDto.getWidth(), "width");
        requirePositiveDimension(packageDto.getDepth(), "depth");
        final Package pack = new Package(
                label, packageDto.getHeight(), packageDto.getWidth(), packageDto.getDepth());
        return packageRepository.save(pack);
    }

    @Override
    @Transactional
    public Package updatePackage(PackageDto packageDto) {
        final String label = requireNonBlankLabel(packageDto.getLabel());
        requirePositiveDimension(packageDto.getHeight(), "height");
        requirePositiveDimension(packageDto.getWidth(), "width");
        requirePositiveDimension(packageDto.getDepth(), "depth");
        final Package pack = getPackageOrDie(packageDto.getId());
        pack.setLabel(label)
                .setHeight(packageDto.getHeight())
                .setWidth(packageDto.getWidth())
                .setDepth(packageDto.getDepth());
        return packageRepository.save(pack);
    }

    @Override
    @Transactional
    public void deletePackage(String packageId) {
        final Package pack = getPackageOrDie(packageId);
        if (productRepository.existsByPackaging(pack)) {
            throw new IllegalArgumentException("Package with label [" + pack.getLabel() + "] contains products.");
        }
        packageRepository.delete(pack);
    }

    private static String requireNonBlankLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Package label must not be blank");
        }
        return label.trim();
    }

    private static void requirePositiveDimension(float dimension, String field) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("Package " + field + " must be a positive value");
        }
    }
}
