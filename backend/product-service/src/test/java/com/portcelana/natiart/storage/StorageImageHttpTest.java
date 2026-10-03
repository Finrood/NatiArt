package com.portcelana.natiart.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.portcelana.natiart.configuration.ControllerAdvice;
import com.portcelana.natiart.controller.ProductController;
import com.portcelana.natiart.repository.CartItemRepository;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.service.CategoryManager;
import com.portcelana.natiart.service.ImageConversionService;
import com.portcelana.natiart.service.PackageManager;
import com.portcelana.natiart.service.ProductManagerImpl;
import com.portcelana.natiart.service.CategoryManager;

class StorageImageHttpTest {
    @TempDir
    Path temporary;

    private Path root;
    private Path legacy;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        root = Files.createDirectory(temporary.resolve("images"));
        legacy = temporary.resolve("previous-release");
        final StorageFileSystem storage = new StorageFileSystem(List.of(root.toString()), List.of(legacy.toString()));
        final ProductManagerImpl manager = new ProductManagerImpl(
                mock(ProductRepository.class),
                mock(OrderRepository.class),
                mock(CartItemRepository.class),
                mock(CategoryManager.class),
                mock(PackageManager.class),
                new StorageServiceImpl(List.of(storage)));
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(manager, mock(CategoryManager.class), mock(ImageConversionService.class)))
                .setControllerAdvice(new ControllerAdvice())
                .build();
    }

    @Test
    void logicalAndLegacyMissingAndDisallowedPathsReturnStatic404() throws Exception {
        for (String path : List.of(
                "file:missing.webp",
                legacy.resolve("missing.webp").toUri().toString(),
                temporary.resolve("outside.webp").toUri().toString(),
                "file:../outside.webp")) {
            assertResponse(path, 404, "Requested image is not available");
        }
    }

    @Test
    void directoryAndEscapingSymlinkReturnStatic404() throws Exception {
        final Path directory = Files.createDirectory(root.resolve("directory"));
        final Path outside = Files.writeString(temporary.resolve("secret"), "private");
        Files.createSymbolicLink(root.resolve("link"), outside);
        assertResponse(directory.toUri().toString(), 404, "Requested image is not available");
        assertResponse("file:link", 404, "Requested image is not available");
    }

    @Test
    void deniedFileIs404ButOperationalFailureRemains500() throws Exception {
        final Path denied = Files.writeString(root.resolve("denied"), "private");
        final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(denied);
        try {
            Files.setPosixFilePermissions(denied, Set.of());
            assertFalse(Files.isReadable(denied), "This fixture must run as a non-root user");
            assertResponse("file:denied", 404, "Requested image is not available");
        } finally {
            Files.setPosixFilePermissions(denied, permissions);
        }
        Files.createSymbolicLink(root.resolve("loop"), Path.of("loop"));
        final var response = mvc.perform(get("/images").param("path", "file:loop"))
                .andReturn()
                .getResponse();
        assertEquals(500, response.getStatus());
        assertFalse(response.getContentAsString().contains(root.toString()));
    }

    @Test
    void logicalAndRemappedLegacyFilesReturnActualBytes() throws Exception {
        Files.writeString(root.resolve("existing.webp"), "image-bytes");
        assertResponse("file:existing.webp", 200, "image-bytes");
        assertResponse(legacy.resolve("existing.webp").toUri().toString(), 200, "image-bytes");
    }

    private void assertResponse(String path, int status, String body) throws Exception {
        final var response =
                mvc.perform(get("/images").param("path", path)).andReturn().getResponse();
        assertEquals(status, response.getStatus(), path);
        assertEquals(body, response.getContentAsString());
        assertFalse(response.getContentAsString().contains(root.toString()));
    }
}
