package com.portcelana.natiart.storage;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.util.IOUtils;
import org.apache.poi.util.TempFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;

@Component
public class StorageFileSystem implements Storage {
    private final List<Path> allowedRoots;
    private final List<Path> legacyRoots;

    @Autowired
    public StorageFileSystem(
            @Value("${nati.storage.filesystem.allowed-roots:}") List<String> allowedRoots,
            @Value("${nati.storage.filesystem.legacy-roots:}") List<String> legacyRoots) {
        this(allowedRoots, legacyRoots, Path.of(System.getProperty("user.dir")));
    }

    StorageFileSystem(List<String> allowedRoots, Path workingDirectory) {
        this(allowedRoots, List.of(), workingDirectory);
    }

    private StorageFileSystem(List<String> allowedRoots, List<String> legacyRoots, Path workingDirectory) {
        this.allowedRoots = (allowedRoots == null || allowedRoots.isEmpty()
                        ? defaultAllowedRoots(workingDirectory)
                        : allowedRoots)
                .stream()
                        .map(Path::of)
                        .map(Path::toAbsolutePath)
                        .map(Path::normalize)
                        .toList();
        this.legacyRoots = legacyRoots == null
                ? List.of()
                : legacyRoots.stream()
                        .filter(root -> root != null && !root.isBlank())
                        .map(Path::of)
                        .map(Path::toAbsolutePath)
                        .map(Path::normalize)
                        .toList();
    }

    public StorageFileSystem(List<String> allowedRoots) {
        this(allowedRoots, List.of());
    }

    private static List<String> defaultAllowedRoots(Path workingDirectory) {
        return List.of(
                Path.of(System.getProperty("java.io.tmpdir"), "product-images").toString(),
                workingDirectory.resolve("product-images").toString());
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public boolean support(URI uri) {
        return "file".equals(uri.getScheme());
    }

    @Override
    public InputStream openFile(URI path) {
        final Path file = resolveAllowedFile(path).toPath();
        try {
            final BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) {
                throw new ResourceNotFoundException("Requested image is not available");
            }
            return Files.newInputStream(file, StandardOpenOption.READ);
        } catch (NoSuchFileException | NotDirectoryException | AccessDeniedException e) {
            // The file may also disappear between reading its attributes and opening it.
            throw new ResourceNotFoundException("Requested image is not available");
        } catch (IOException e) {
            throw new IllegalStateException("Error while reading the requested image from local storage.", e);
        }
    }

    private File resolveAllowedFile(URI path) {
        if (!support(path)) {
            throw new IllegalArgumentException("Unsupported URI scheme for file storage: " + path);
        }
        if (path.isOpaque()) {
            try {
                return resolvePrimaryFile(validRelativeKey(path.getSchemeSpecificPart()))
                        .toFile();
            } catch (IllegalArgumentException e) {
                throw new ResourceNotFoundException("Requested image is not available");
            }
        }
        final File candidate = new File(path);
        final Path normalizedCandidate;
        try {
            normalizedCandidate = candidate.getCanonicalFile().toPath();
        } catch (IOException e) {
            throw new IllegalStateException("Error while resolving the requested image in local storage.", e);
        }
        for (Path root : allowedRoots) {
            if (normalizedCandidate.startsWith(root)) {
                return normalizedCandidate.toFile();
            }
        }
        for (Path legacyRoot : legacyRoots) {
            if (normalizedCandidate.startsWith(legacyRoot)) {
                final Path relative = legacyRoot.relativize(normalizedCandidate);
                return resolvePrimaryFile(relative).toFile();
            }
        }
        throw new ResourceNotFoundException("Requested image is not available");
    }

    private Path resolvePrimaryFile(Path relative) {
        try {
            final Path file = allowedRoots
                    .getFirst()
                    .resolve(relative)
                    .toFile()
                    .getCanonicalFile()
                    .toPath();
            if (!file.startsWith(allowedRoots.getFirst())) {
                throw new ResourceNotFoundException("Requested image is not available");
            }
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to resolve stored image", e);
        }
    }

    private Path validRelativeKey(String key) {
        if (key == null || key.isBlank() || key.contains("..") || key.contains("\\")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        final Path relative = Path.of(key);
        if (relative.isAbsolute() || !relative.normalize().equals(relative)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return relative;
    }

    @Override
    public boolean exists(URI uri) {
        return resolveAllowedFile(uri).exists();
    }

    @Override
    public URI uploadFile(String key, InputFile inputFile) {
        return uploadFile(allowedRoots.get(0).toString(), key, inputFile);
    }

    @Override
    public URI uploadFile(String location, String key, InputFile inputFile) {
        try (InputStream inputStream = inputFile.inputStream()) {
            return writeFile(resolveAllowedWriteFile(location, key), inputStream);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to close an image upload", error);
        }
    }

    private URI writeFile(File file, InputStream inputStream) {
        boolean created = false;
        try {
            Files.createDirectories(file.toPath().getParent());
            try (OutputStream outputStream =
                    Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                IOUtils.copy(inputStream, outputStream);
            }
            return storedKey(file);
        } catch (IOException error) {
            if (created) {
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (IOException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            throw new IllegalStateException("Unable to store an image", error);
        }
    }

    @Override
    public URI uploadTarget(String location, String key) {
        return storedKey(resolveAllowedWriteFile(location, key));
    }

    private URI storedKey(File file) {
        final String relative =
                allowedRoots.getFirst().relativize(file.toPath()).toString().replace('\\', '/');
        try {
            return new URI("file", relative, null);
        } catch (URISyntaxException error) {
            throw new IllegalStateException("Unable to encode stored image key", error);
        }
    }

    @Override
    public void delete(URI path) {
        final File file = resolveAllowedFile(path);
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to remove a stored image", e);
        }
    }

    @Override
    public void deleteFile(URI uri) {
        if (!uri.isOpaque()) {
            throw new IllegalArgumentException("Deletion requires a logical storage key");
        }
        try {
            Files.deleteIfExists(resolveAllowedFile(uri).toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to delete stored file", e);
        }
    }

    /**
     * Resolves a write target under one of the allowed roots, mirroring the read-path
     * confinement in {@link #resolveAllowedFile(URI)} so uploads cannot escape via
     * {@code ..} segments or absolute paths.
     */
    private File resolveAllowedWriteFile(String location, String key) {
        if (location == null || location.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("Storage write requires a non-blank location and key");
        }
        final Path relative = validRelativeKey(key);
        final Path normalizedCandidate;
        try {
            final Path base = Path.of(location).isAbsolute()
                    ? Path.of(location)
                    : allowedRoots.getFirst().resolve(location);
            normalizedCandidate =
                    base.resolve(relative).toFile().getCanonicalFile().toPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to resolve storage write path: " + key);
        }
        if (normalizedCandidate.startsWith(allowedRoots.getFirst())) {
            return normalizedCandidate.toFile();
        }
        throw new IllegalArgumentException("Storage write path is outside of the allowed storage roots: " + key);
    }

    @Override
    public InputStream downloadFiles(Set<URI> uriSet) {
        try {
            final File tempFile = TempFile.createTempFile("zip-file", "");
            try (final ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(tempFile))) {
                final Set<String> usedEntryNames = new HashSet<>();
                uriSet.stream().sorted(Comparator.comparing(URI::toString)).forEach((uri) -> {
                    final String fileName = uniqueZipEntryName(
                            usedEntryNames, Paths.get(uri.isOpaque() ? uri.getSchemeSpecificPart() : uri.getPath()));
                    addZipEntry(zip, fileName, resolveAllowedFile(uri));
                });
            }
            return Files.newInputStream(tempFile.toPath(), StandardOpenOption.DELETE_ON_CLOSE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Zip entries are named from the file basename, so files from different
     * directories can collide (extraction would silently keep one). Collisions
     * are disambiguated with the parent directory name, then a counter.
     */
    private static String uniqueZipEntryName(Set<String> usedNames, Path path) {
        final String baseName = path.getFileName().toString();
        if (usedNames.add(baseName)) {
            return baseName;
        }
        final Path parent = path.getParent();
        final String parentName = parent == null || parent.getFileName() == null
                ? "file"
                : parent.getFileName().toString();
        String candidate = parentName + "-" + baseName;
        for (int index = 2; !usedNames.add(candidate); index++) {
            candidate = parentName + "-" + index + "-" + baseName;
        }
        return candidate;
    }

    @Override
    public InputStream downloadDirectory(URI uri) {
        try {
            final File directory = resolveAllowedFile(uri);
            if (!directory.isDirectory()) {
                throw new ResourceNotFoundException("Requested path is not a directory: " + uri);
            }
            final File zipFile = TempFile.createTempFile("zip-file", "");

            try (final ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile))) {
                zipFileRecursively(directory, directory.getName(), zip);
                return Files.newInputStream(zipFile.toPath(), StandardOpenOption.DELETE_ON_CLOSE);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void zipFileRecursively(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (Files.isSymbolicLink(fileToZip.toPath())) {
            // Never follow links out of the confined tree: a symlink planted inside an
            // allowed root must not exfiltrate its target's bytes into the zip.
            return;
        }
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            }
            final File[] children = fileToZip.listFiles();
            if (children != null) {
                for (File childFile : children) {
                    zipFileRecursively(childFile, fileName + "/" + childFile.getName(), zipOut);
                }
            }
        } else {
            addZipEntry(zipOut, fileName, fileToZip);
        }
    }

    private void addZipEntry(ZipOutputStream zip, String name, File fileToZip) {
        try (final FileInputStream fis = new FileInputStream(fileToZip)) {
            final ZipEntry zipEntry = new ZipEntry(name);
            zip.putNextEntry(zipEntry);
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zip.write(bytes, 0, length);
            }
            zip.closeEntry();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addZipEntry(ZipOutputStream zip, String name, URI uri) {
        try (final InputStream inputStream = openFile(uri)) {
            int length;
            byte[] buffer = new byte[1024];
            zip.putNextEntry(new ZipEntry(name));
            while ((length = inputStream.read(buffer)) > 0) {
                zip.write(buffer, 0, length);
            }
            zip.closeEntry();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
