package com.portcelana.natiart.storage;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.TempFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;

@Component
public class StorageFileSystem implements Storage {
    private static final List<String> DEFAULT_ALLOWED_ROOTS = List.of(
            System.getProperty("java.io.tmpdir") + "/product-images",
            System.getProperty("user.dir") + "/product-images");

    private final List<Path> allowedRoots;

    public StorageFileSystem(@Value("${nati.storage.filesystem.allowed-roots:}") List<String> allowedRoots) {
        this.allowedRoots = (allowedRoots == null || allowedRoots.isEmpty() ? DEFAULT_ALLOWED_ROOTS : allowedRoots)
                .stream()
                        .map(Path::of)
                        .map(Path::toAbsolutePath)
                        .map(Path::normalize)
                        .toList();
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
        final File file = resolveAllowedFile(path);
        try {
            return FileUtils.openInputStream(file);
        } catch (IOException e) {
            throw new IllegalStateException(
                    String.format("Error while reading file [%s] on local storage.", file.getName()), e);
        }
    }

    private File resolveAllowedFile(URI path) {
        if (!support(path)) {
            throw new IllegalArgumentException("Unsupported URI scheme for file storage: " + path);
        }
        final File candidate = new File(path);
        final Path normalizedCandidate;
        try {
            normalizedCandidate = candidate.getCanonicalFile().toPath();
        } catch (IOException e) {
            throw new ResourceNotFoundException("Unable to resolve requested path: " + path);
        }
        for (Path root : allowedRoots) {
            if (normalizedCandidate.startsWith(root)) {
                return normalizedCandidate.toFile();
            }
        }
        throw new ResourceNotFoundException("Requested path is outside of the allowed storage roots: " + path);
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
        final File file = resolveAllowedWriteFile(location, key);
        try {
            Files.createDirectories(file.toPath().getParent());
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                IOUtils.copy(inputFile.inputStream(), fileOutputStream);
            }
            return file.toURI();
        } catch (IOException e) {
            throw new IllegalStateException(
                    String.format("An error has occurred while storing file [%s] in [%s]", file.getName(), key));
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
        if (Path.of(key).isAbsolute() || key.contains("..")) {
            throw new IllegalArgumentException("Storage write key escapes the allowed storage roots: " + key);
        }
        final Path normalizedCandidate;
        try {
            normalizedCandidate = new File(location, key).getCanonicalFile().toPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to resolve storage write path: " + key);
        }
        for (Path root : allowedRoots) {
            if (normalizedCandidate.startsWith(root)) {
                return normalizedCandidate.toFile();
            }
        }
        throw new IllegalArgumentException("Storage write path is outside of the allowed storage roots: " + key);
    }

    @Override
    public InputStream downloadFiles(Set<URI> uriSet) {
        try {
            final File tempFile = TempFile.createTempFile("zip-file", "");
            try (final ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(tempFile))) {
                uriSet.forEach((uri) -> {
                    final String path = uri.getPath();
                    final String fileName = Paths.get(path).getFileName().toString();
                    addZipEntry(zip, fileName, resolveAllowedFile(uri));
                });
            }
            return Files.newInputStream(tempFile.toPath(), StandardOpenOption.DELETE_ON_CLOSE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
