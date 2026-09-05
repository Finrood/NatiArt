package com.portcelana.natiart.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import com.portcelana.natiart.dto.PackageDto;
import com.portcelana.natiart.service.PackageManager;

@RestController
public class PackageController {
    private final PackageManager packageManager;

    public PackageController(PackageManager packageManager) {
        this.packageManager = packageManager;
    }

    @GetMapping("/packages/{packageId}")
    public PackageDto getPackage(@PathVariable String packageId) {
        return PackageDto.from(packageManager.getPackageOrDie(packageId));
    }

    @GetMapping("/packages")
    public List<PackageDto> getPackages() {
        return packageManager.getPackages().stream()
                .map(PackageDto::from)
                .sorted(Comparator.comparing(PackageDto::getLabel))
                .toList();
    }

    @PostMapping("/packages/create")
    @PreAuthorize("hasRole('ADMIN')")
    public PackageDto createPackage(@RequestBody PackageDto packageDto) {
        return PackageDto.from(packageManager.createPackage(packageDto));
    }

    @PutMapping("/packages/{packageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public PackageDto updatePackage(@PathVariable String packageId, @RequestBody PackageDto packageDto) {
        Assert.isTrue(packageId.equals(packageDto.getId()), "package ids are not equals !");

        return PackageDto.from(packageManager.updatePackage(packageDto));
    }

    @DeleteMapping("/packages/{packageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deletePackage(@PathVariable String packageId) {
        packageManager.deletePackage(packageId);
    }
}
