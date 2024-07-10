package com.portcelana.natiart.storage;

import org.springframework.beans.factory.annotation.Value;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class StorageServiceImpl implements StorageService {

    private final List<Storage> storages = new ArrayList<>();

    private String uploadScheme;

    public StorageServiceImpl(List<Storage> storages) {
        Optional.ofNullable(storages).ifPresent(this.storages::addAll);
    }

    @Value("${nati.storage.upload:gcs}")
    public void setUploadScheme(String uploadScheme) {
        this.uploadScheme = uploadScheme;
    }

    @Override
    public InputStream openFile(URI uri) {
        return getStorage(uri).openFile(uri);
    }

    @Override
    public boolean exists(URI uri) {
        return getStorage(uri).exists(uri);
    }

    @Override
    public URI uploadFile(String key, InputFile file) {
        return getStorage(uploadScheme).uploadFile(key, file);
    }

    @Override
    public URI uploadFile(String location, InputFile file, String key) {
        return getStorage(uploadScheme).uploadFile(location, key, file);
    }

    @Override
    public InputStream downloadFiles(Set<URI> uriSet) {
        final Storage storage = getStorage(uriSet.iterator().next());
        return storage.downloadFiles(uriSet);
    }

    @Override
    public InputStream downloadDirectory(URI uri) {
        return getStorage(uri).downloadDirectory(uri);
    }

    // ----------------------------------------------------------------------

    private Storage getStorage(URI uri) {
        return storages.stream()
                .filter(manager -> manager.support(uri))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(String.format("There is no manager handling the uri [%s]", uri)));
    }

    private Storage getStorage(String name) {
        return storages.stream()
                .filter(manager -> manager.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(String.format("There is no manager with name [%s]", name)));
    }
}
