package com.portcelana.natiart.controller;

import com.portcelana.natiart.dto.PackageDto;
import com.portcelana.natiart.service.PackageManager;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
public class PackageController {
    final private PackageManager packageManager;

    public PackageController(PackageManager packageManager) {
        this.packageManager = packageManager;
    }

    @GetMapping("/packages/{packageId}")
    public PackageDto getCategory(@PathVariable String packageId) {
        return PackageDto.from(packageManager.getPackageOrDie(packageId));
    }

    @GetMapping("/packages")
    public List<PackageDto> getCategories() {
        return packageManager.getPackages().stream()
                .map(PackageDto::from)
                .sorted(Comparator.comparing(PackageDto::getLabel))
                .toList();
    }

    @PostMapping("/packages/create")
    public PackageDto createCategory(@RequestBody PackageDto packageDto) {
        return PackageDto.from(packageManager.createPackage(packageDto));
    }

    @PutMapping("/packages/{packageId}")
    public PackageDto updateCategory(@PathVariable String packageId, @RequestBody PackageDto packageDto) {
        Assert.isTrue(packageId.equals(packageDto.getId()), "package ids are not equals !");

        return PackageDto.from(packageManager.updatePackage(packageDto));
    }

    @DeleteMapping("/packages/{packageId}")
    public void deleteCategory(@PathVariable String packageId) {
        packageManager.deletePackage(packageId);
    }
}
