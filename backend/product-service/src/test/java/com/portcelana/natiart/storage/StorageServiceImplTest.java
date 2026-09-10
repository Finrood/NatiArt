package com.portcelana.natiart.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
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

    @Test
    void openFile_unsupportedScheme_throwsBadRequestWithStaticMessage() {
        final StorageServiceImpl service = new StorageServiceImpl(List.of(new StorageFileSystem(List.of())));

        final IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> service.openFile(URI.create("gcs://bucket/x")));

        assertEquals("Unsupported file location", thrown.getMessage());
    }

    @Test
    void exists_unsupportedScheme_throwsBadRequestInsteadOfServerError() {
        final StorageServiceImpl service = new StorageServiceImpl(List.of(new StorageFileSystem(List.of())));

        assertThrows(IllegalArgumentException.class, () -> service.exists(URI.create("gcs://bucket/x")));
    }
}
