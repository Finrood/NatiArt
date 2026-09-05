package com.portcelana.natiart.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class StorageServiceImplTest {

    private final StorageServiceImpl storageService = new StorageServiceImpl(List.of());

    @Test
    void downloadFiles_rejectsEmptySetWithBadRequest() {
        assertThrows(IllegalArgumentException.class, () -> storageService.downloadFiles(Set.of()));
    }

    @Test
    void downloadFiles_rejectsNullSetWithBadRequest() {
        assertThrows(IllegalArgumentException.class, () -> storageService.downloadFiles(null));
    }
}
