package com.portcelana.natiart.storage;

import java.io.InputStream;
import java.net.URI;
import java.util.Set;

public interface Storage {

    String getName();

    boolean support(URI uri);

    InputStream openFile(URI uri);

    boolean exists(URI uri);

    URI uploadFile(String key, InputFile file);

    URI uploadFile(String location, String key, InputFile file);

    /** Resolve the confined target before writing so durable ownership can be recorded. */
    URI uploadTarget(String location, String key);

    /** Idempotently remove an owned file. */
    void delete(URI uri);

    void deleteFile(URI uri);

    InputStream downloadFiles(Set<URI> uriSet);

    InputStream downloadDirectory(URI uri);
}
