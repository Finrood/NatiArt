package com.portcelana.natiart.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import com.portcelana.natiart.dto.PackageDto;
import com.portcelana.natiart.service.PackageManager;

@RestController
public class PackageController {
    private static final int MAX_PAGE_SIZE = 100;

    private final PackageManager packageManager;

    public PackageController(PackageManager packageManager) {
        this.packageManager = packageManager;
    }

    @GetMapping("/packages/{packageId}")
    public PackageDto getPackage(@PathVariable String packageId) {
        return PackageDto.from(packageManager.getPackageOrDie(packageId));
    }

    @GetMapping("/packages")
    public List<PackageDto> getPackages(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return packageManager.getPackages(toPageable(page, size)).stream()
                .map(PackageDto::from)
                .toList();
    }

    private static Pageable toPageable(int page, int size) {
        final int safePage = Math.max(0, page);
        final int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "label"));
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
