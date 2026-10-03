package com.portcelana.natiart.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;

class StorageFileSystemTest {

    @TempDir
    Path tempDir;

    private StorageFileSystem storageWithRoots(List<String> roots) {
        return new StorageFileSystem(roots);
    }

    private StorageFileSystem storageWithRoots(List<String> roots, Path workingDirectory) {
        return new StorageFileSystem(roots, workingDirectory);
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

        ResourceNotFoundException thrown =
                assertThrows(ResourceNotFoundException.class, () -> storage.openFile(URI.create("file:///etc/passwd")));
        assertEquals("Requested image is not available", thrown.getMessage());
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
    void openFileReturnsNotFoundForMissingFileWithoutDisclosingPath() throws IOException {
        Path root = tempDir.resolve("product-images");
        Files.createDirectories(root);
        Path missing = root.resolve("retired-product/missing.webp");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        ResourceNotFoundException thrown =
                assertThrows(ResourceNotFoundException.class, () -> storage.openFile(missing.toUri()));

        assertEquals("Requested image is not available", thrown.getMessage());
        assertFalse(thrown.getMessage().contains(root.toString()));
    }

    @Test
    void openFileReturnsNotFoundForAccessDenied() throws IOException {
        Path root = tempDir.resolve("product-images");
        Path denied = Path.of(writeInside(root, "p1/denied.webp", "image-bytes"));
        Assumptions.assumeTrue(Files.getFileStore(denied).supportsFileAttributeView("posix"));
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(denied);
        try {
            Files.setPosixFilePermissions(denied, Set.of());
            Assumptions.assumeFalse(Files.isReadable(denied));
            StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

            ResourceNotFoundException thrown =
                    assertThrows(ResourceNotFoundException.class, () -> storage.openFile(denied.toUri()));

            assertEquals("Requested image is not available", thrown.getMessage());
            assertFalse(thrown.getMessage().contains(root.toString()));
        } finally {
            Files.setPosixFilePermissions(denied, originalPermissions);
        }
    }

    @Test
    void openFileReturnsNotFoundForDirectory() throws IOException {
        Path root = tempDir.resolve("product-images");
        Path directory = root.resolve("retired-product");
        Files.createDirectories(directory);
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        ResourceNotFoundException thrown =
                assertThrows(ResourceNotFoundException.class, () -> storage.openFile(directory.toUri()));

        assertEquals("Requested image is not available", thrown.getMessage());
    }

    @Test
    void openFileRejectsUnsupportedScheme() {
        StorageFileSystem storage = storageWithRoots(List.of(tempDir.toString()));

        assertThrows(
                IllegalArgumentException.class, () -> storage.openFile(URI.create("https://evil.example.com/secret")));
    }

    @Test
    void openFileFallsBackToDefaultRootsWhenUnconfigured() throws IOException {
        Path applicationDirectory = tempDir.resolve("application");
        Path cwdImages = applicationDirectory.resolve("product-images");
        URI uri = writeInside(cwdImages, "fallback-test/img.webp", "image-bytes");
        Path sentinel = Path.of(writeInside(cwdImages, "existing/sentinel.txt", "must-survive"));
        Path externalSentinel = tempDir.resolve("external-sentinel.txt");
        Files.writeString(externalSentinel, "external-must-survive");
        Path link = cwdImages.resolve("existing/external-link");
        try {
            Files.createSymbolicLink(link, externalSentinel);
        } catch (IOException | UnsupportedOperationException e) {
            link = null;
        }

        StorageFileSystem storage = storageWithRoots(List.of(), applicationDirectory);
        try (var in = storage.openFile(uri)) {
            assertEquals("image-bytes", new String(in.readAllBytes()));
        }

        assertEquals("must-survive", Files.readString(sentinel));
        assertEquals("external-must-survive", Files.readString(externalSentinel));
        if (link != null) {
            assertTrue(Files.isSymbolicLink(link));
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
    void downloadFilesDisambiguatesDuplicateBasenames() throws IOException {
        Path root = tempDir.resolve("product-images");
        URI first = writeInside(root, "p1/a.webp", "aaa");
        URI second = writeInside(root, "p2/a.webp", "bbb");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        try (var in = storage.downloadFiles(Set.of(first, second));
                var zipIn = new ZipInputStream(in)) {
            java.util.Map<String, String> contents = new HashMap<>();
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                contents.put(entry.getName(), new String(zipIn.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertEquals(Set.of("a.webp", "p2-a.webp"), contents.keySet());
            assertEquals(Set.of("aaa", "bbb"), Set.copyOf(contents.values()));
        }
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
    void downloadDirectorySkipsSymlinkThatEscapesAllowedRoot() throws IOException {
        Path root = tempDir.resolve("product-images");
        writeInside(root, "gallery/real.webp", "real-bytes");
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "classified");
        try {
            Files.createSymbolicLink(root.resolve("gallery/escape-link"), secret);
        } catch (IOException | UnsupportedOperationException e) {
            return; // filesystem without symlink support
        }
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        try (var in = storage.downloadDirectory(root.resolve("gallery").toUri());
                var zipIn = new ZipInputStream(in)) {
            List<String> names = new ArrayList<>();
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                names.add(entry.getName());
                assertNotEquals(
                        "classified",
                        new String(zipIn.readAllBytes(), StandardCharsets.UTF_8),
                        "zip must never contain bytes from outside the allowed roots");
            }
            assertTrue(names.contains("gallery/real.webp"));
            assertFalse(names.stream().anyMatch(name -> name.contains("escape-link")));
        }
    }

    @Test
    void uploadFileWritesInsideAllowedRoot() throws IOException {
        Path root = tempDir.resolve("product-images");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        final URI uri = storage.uploadFile(root.resolve("p1").toString(), "img.webp", testInput("image-bytes"));

        assertEquals("file:p1/img.webp", uri.toString());
        assertTrue(Files.exists(root.resolve("p1/img.webp")));
        try (var in = storage.openFile(uri)) {
            assertEquals("image-bytes", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void uploadFileDefaultsToFirstAllowedRoot() throws IOException {
        Path root = tempDir.resolve("product-images");
        StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        final URI uri = storage.uploadFile("default.webp", testInput("default-bytes"));

        assertEquals("file:default.webp", uri.toString());
        assertTrue(Files.exists(root.resolve("default.webp")));
        try (var in = storage.openFile(uri)) {
            assertEquals("default-bytes", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void uploadedKeySurvivesAdapterReplacementAndNonDefaultRoot() throws IOException {
        final Path firstRoot = tempDir.resolve("first-volume");
        final StorageFileSystem firstInstance = storageWithRoots(List.of(firstRoot.toString()));
        final URI uri = firstInstance.uploadFile("products/product-1/image.webp", testInput("saved-bytes"));

        assertEquals("file:products/product-1/image.webp", uri.toString());
        try (var in = storageWithRoots(List.of(firstRoot.toString())).openFile(uri)) {
            assertEquals("saved-bytes", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }

        final Path relocatedRoot = tempDir.resolve("non-default-volume");
        final Path relocatedFile = relocatedRoot.resolve("products/product-1/image.webp");
        Files.createDirectories(relocatedFile.getParent());
        Files.copy(firstRoot.resolve("products/product-1/image.webp"), relocatedFile);
        try (var in = storageWithRoots(List.of(relocatedRoot.toString())).openFile(uri)) {
            assertEquals("saved-bytes", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void deleteFileRemovesOnlyLogicalKeyInsideConfiguredRoot() throws IOException {
        final Path root = tempDir.resolve("volume");
        final StorageFileSystem storage = storageWithRoots(List.of(root.toString()));
        final URI artwork = storage.uploadFile("customer-uploads/artwork.webp", testInput("artwork"));
        final Path outside = tempDir.resolve("outside.webp");
        Files.writeString(outside, "keep");

        storage.deleteFile(artwork);
        storage.deleteFile(artwork);

        assertFalse(Files.exists(root.resolve("customer-uploads/artwork.webp")));
        assertThrows(ResourceNotFoundException.class, () -> storage.deleteFile(URI.create("file:../outside.webp")));
        assertThrows(IllegalArgumentException.class, () -> storage.deleteFile(outside.toUri()));
        assertEquals("keep", Files.readString(outside));
    }

    @Test
    void legacyAbsoluteUriResolvesCopiedFileUnderConfiguredRoot() throws IOException {
        final Path root = tempDir.resolve("new-volume");
        final Path oldRoot = tempDir.resolve("old-process-directory/product-images");
        final Path file = root.resolve("product-1/old.webp");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "legacy-bytes");
        final StorageFileSystem storage = new StorageFileSystem(List.of(root.toString()), List.of(oldRoot.toString()));

        try (var in = storage.openFile(oldRoot.resolve("product-1/old.webp").toUri())) {
            assertEquals("legacy-bytes", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertThrows(
                ResourceNotFoundException.class,
                () -> storage.openFile(
                        tempDir.resolve("unknown/product-1/old.webp").toUri()));
    }

    @Test
    void logicalKeyRejectsTraversalAndEscapingSymlink() throws IOException {
        final Path root = tempDir.resolve("volume");
        Files.createDirectories(root);
        final StorageFileSystem storage = storageWithRoots(List.of(root.toString()));

        assertThrows(ResourceNotFoundException.class, () -> storage.openFile(URI.create("file:../secret.webp")));
        assertThrows(
                ResourceNotFoundException.class, () -> storage.openFile(URI.create("file:products/../secret.webp")));
        final Path outside = tempDir.resolve("outside.webp");
        Files.writeString(outside, "secret");
        try {
            Files.createSymbolicLink(root.resolve("link.webp"), outside);
        } catch (IOException | UnsupportedOperationException e) {
            return;
        }
        assertThrows(ResourceNotFoundException.class, () -> storage.openFile(URI.create("file:link.webp")));
    }

    @Test
    void downloadFilesAcceptsLogicalKeys() throws IOException {
        final Path root = tempDir.resolve("volume");
        final StorageFileSystem storage = storageWithRoots(List.of(root.toString()));
        final URI uri = storage.uploadFile("products/product-1/image.webp", testInput("bytes"));

        try (var in = storage.downloadFiles(Set.of(uri));
                var zip = new ZipInputStream(in)) {
            assertEquals("image.webp", zip.getNextEntry().getName());
            assertEquals("bytes", new String(zip.readAllBytes(), StandardCharsets.UTF_8));
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
}
