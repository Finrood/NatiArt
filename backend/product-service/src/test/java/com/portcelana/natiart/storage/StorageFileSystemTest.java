package com.portcelana.natiart.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;

class StorageFileSystemTest {

    @TempDir
    Path tempDir;

    private StorageFileSystem storageWithRoots(List<String> roots) {
        return new StorageFileSystem(roots);
    }

    private URI writeInside(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file.toUri();
    }

    @Test
    void openFileAllowsFileWithinAllowedRoot() throws IOException {
        Path root = tempDir.resolve("product-images");
        URI uri = writeInside(root, "p1/img.webp", "image-bytes");

        try (var in = storageWithRoots(List.of(root.toString())).openFile(uri)) {
            assertEquals("image-bytes", new String(in.readAllBytes()));
        }
    }

    @Test
    void openFileRejectsTraversalOutsideAllowedRoot() throws IOException {
        Path root = tempDir.resolve("product-images");
        writeInside(root, "p1/img.webp", "image-bytes");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        assertThrows(ResourceNotFoundException.class, () -> storage.openFile(URI.create("file:///etc/passwd")));
    }

    @Test
    void openFileRejectsRelativeEscapeFromAllowedRoot() throws IOException {
        Path root = tempDir.resolve("product-images");
        Files.createDirectories(root);
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        URI escape = URI.create(root.toUri().toString() + "../secret.txt");
        assertThrows(ResourceNotFoundException.class, () -> storage.openFile(escape));
    }

    @Test
    void openFileRejectsUnsupportedScheme() {
        StorageFileSystem storage = storageWithRoots(List.of(tempDir.toString()));

        assertThrows(
                IllegalArgumentException.class, () -> storage.openFile(URI.create("https://evil.example.com/secret")));
    }

    @Test
    void openFileFallsBackToDefaultRootsWhenUnconfigured() throws IOException {
        Path cwdImages = Path.of(System.getProperty("user.dir"), "product-images");
        URI uri = writeInside(cwdImages, "fallback-test/img.webp", "image-bytes");
        try {
            StorageFileSystem storage = storageWithRoots(List.of());
            try (var in = storage.openFile(uri)) {
                assertEquals("image-bytes", new String(in.readAllBytes()));
            }
        } finally {
            cleanupRecursively(cwdImages);
        }
    }

    @Test
    void existsUsesSameRestrictions() throws IOException {
        Path root = tempDir.resolve("product-images");
        URI uri = writeInside(root, "p1/exists.webp", "x");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        assertTrue(storage.exists(uri));
        assertThrows(ResourceNotFoundException.class, () -> storage.exists(URI.create("file:///etc/passwd")));
    }

    @Test
    void openFileRejectsSymlinkThatEscapesAllowedRoot() throws IOException {
        Path root = tempDir.resolve("product-images");
        Files.createDirectories(root);
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "classified");
        Path link = root.resolve("evil-link");
        try {
            Files.createSymbolicLink(link, secret);
        } catch (IOException | UnsupportedOperationException e) {
            return; // filesystem without symlink support
        }
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        assertThrows(ResourceNotFoundException.class, () -> storage.openFile(link.toUri()));
    }

    @Test
    void downloadFilesZipsFilesUnderAllowedRoot() throws IOException {
        Path root = tempDir.resolve("product-images");
        URI a = writeInside(root, "p1/a.webp", "aaa");
        URI b = writeInside(root, "p2/b.webp", "bbb");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        try (var in = storage.downloadFiles(Set.of(a, b));
                var zipIn = new ZipInputStream(in)) {
            List<String> names = new ArrayList<>();
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                names.add(entry.getName());
            }
            assertEquals(Set.of("a.webp", "b.webp"), Set.copyOf(names));
        }
    }

    @Test
    void downloadFilesRejectsOutsideRoot() {
        StorageFileSystem storage =
                storageWithRoots(List.of(tempDir.resolve("product-images").toString()));

        assertThrows(
                ResourceNotFoundException.class, () -> storage.downloadFiles(Set.of(URI.create("file:///etc/passwd"))));
    }

    @Test
    void downloadDirectoryZipsContentsRecursively() throws IOException {
        Path root = tempDir.resolve("product-images");
        writeInside(root, "gallery/cat.webp", "cat");
        writeInside(root, "gallery/dog.webp", "dog");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        try (var in = storage.downloadDirectory(root.resolve("gallery").toUri());
                var zipIn = new ZipInputStream(in)) {
            List<String> names = new ArrayList<>();
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                names.add(entry.getName());
            }
            assertTrue(names.contains("gallery/cat.webp"));
            assertTrue(names.contains("gallery/dog.webp"));
        }
    }

    @Test
    void downloadDirectoryRejectsNonDirectoryPath() throws IOException {
        Path root = tempDir.resolve("product-images");
        URI file = writeInside(root, "p1/img.webp", "x");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        assertThrows(ResourceNotFoundException.class, () -> storage.downloadDirectory(file));
    }

    @Test
    void downloadDirectoryRejectsOutsideRoot() {
        StorageFileSystem storage =
                storageWithRoots(List.of(tempDir.resolve("product-images").toString()));

        assertThrows(ResourceNotFoundException.class, () -> storage.downloadDirectory(URI.create("file:///etc")));
    }

    @Test
    void uploadFileWritesInsideAllowedRoot() throws IOException {
        Path root = tempDir.resolve("product-images");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        final URI uri = storage.uploadFile(root.resolve("p1").toString(), "img.webp", testInput("image-bytes"));

        assertTrue(Path.of(uri).startsWith(root));
        try (var in = storage.openFile(uri)) {
            assertEquals("image-bytes", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void uploadFileDefaultsToFirstAllowedRoot() throws IOException {
        Path root = tempDir.resolve("product-images");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        final URI uri = storage.uploadFile("default.webp", testInput("default-bytes"));

        assertTrue(Path.of(uri).startsWith(root));
        try (var in = storage.openFile(uri)) {
            assertEquals("default-bytes", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void uploadFileRejectsTraversalKey() {
        Path root = tempDir.resolve("product-images");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        assertThrows(
                IllegalArgumentException.class,
                () -> storage.uploadFile(root.toString(), "../evil.webp", testInput("evil")));
    }

    @Test
    void uploadFileRejectsAbsoluteKey() {
        Path root = tempDir.resolve("product-images");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        assertThrows(
                IllegalArgumentException.class,
                () -> storage.uploadFile(
                        root.toString(), tempDir.resolve("evil.webp").toString(), testInput("evil")));
    }

    @Test
    void uploadFileRejectsLocationOutsideAllowedRoots() {
        Path root = tempDir.resolve("product-images");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        assertThrows(
                IllegalArgumentException.class,
                () -> storage.uploadFile(tempDir.resolve("elsewhere").toString(), "img.webp", testInput("x")));
    }

    private InputFile testInput(String content) {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new InputFile(new ByteArrayInputStream(bytes), "image/webp", "img.webp", bytes.length);
    }

    private void cleanupRecursively(Path path) {
        if (path == null || !Files.exists(path)) return;
        File dir = path.toFile();
        File[] entries = dir.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) cleanupRecursively(entry.toPath());
                else entry.delete();
            }
        }
        dir.delete();
    }
}
