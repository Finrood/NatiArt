package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.repository.PackageRepository;
import com.portcelana.natiart.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class PackageManagerImplTest {

    @Mock
    private PackageRepository packageRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private PackageManagerImpl packageManager;

    @Test
    void deletePackage_withProducts_throwsWithoutDeleting() {
        final Package pack = new Package("box", 1.0f, 1.0f, 1.0f);
        when(packageRepository.findById(pack.getId())).thenReturn(Optional.of(pack));
        when(productRepository.existsByPackaging(pack)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> packageManager.deletePackage(pack.getId()));

        verify(packageRepository, never()).delete(pack);
    }

    @Test
    void deletePackage_withoutProducts_deletes() {
        final Package pack = new Package("box", 1.0f, 1.0f, 1.0f);
        when(packageRepository.findById(pack.getId())).thenReturn(Optional.of(pack));
        when(productRepository.existsByPackaging(pack)).thenReturn(false);

        packageManager.deletePackage(pack.getId());

        verify(packageRepository).delete(pack);
    }
}
