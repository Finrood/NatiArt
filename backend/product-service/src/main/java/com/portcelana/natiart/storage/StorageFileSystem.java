package com.portcelana.natiart.storage;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import org.apache.commons.io.FileUtils;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.TempFile;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class StorageFileSystem implements Storage {
    private final static String DEFAULT_LOCATION = "/tmp";

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
        final File file = new File(path);
        if (!file.exists()) {
            throw new ResourceNotFoundException("Unable to found file on file system for path: " + path);
        }
        try {
            return FileUtils.openInputStream(file);
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Error while reading file [%s] on local storage.", file.getName()), e);
        }
    }

    @Override
    public boolean exists(URI uri) {
        final File file = new File(uri);
        return file.exists();
    }

    @Override
    public URI uploadFile(String key, InputFile inputFile) {
        return uploadFile(DEFAULT_LOCATION, key, inputFile);
    }

    @Override
    public URI uploadFile(String location, String key, InputFile inputFile) {
        final File file = new File(location, key);
        try {
            Files.createDirectories(file.toPath().getParent());
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                IOUtils.copy(inputFile.inputStream(), fileOutputStream);
            }
            return file.toURI();
        } catch (IOException e) {
            throw new IllegalStateException(String.format("An error has occurred while storing file [%s] in [%s]", file.getName(), key));
        }
    }

    @Override
    public InputStream downloadFiles(Set<URI> uriSet) {
        try {
            final File tempFile = TempFile.createTempFile("zip-file", "");
            try (final ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(tempFile))) {
                uriSet.forEach((uri) -> {
                    final String path = uri.getPath();
                    final String fileName = Paths.get(path).getFileName().toString();
                    addZipEntry(zip, fileName, uri);
                });
                return Files.newInputStream(tempFile.toPath(), StandardOpenOption.DELETE_ON_CLOSE);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InputStream downloadDirectory(URI uri) {
        try {
            final File directory = new File(uri);
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
