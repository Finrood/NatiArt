package com.portcelana.natiart.storage;

import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface StorageService extends Serializable {

    /**
     * Get a file content from the storage
     * @param uri of the wanted file
     * @return the file content
     */
    InputStream openFile(URI uri);

    default InputStream openFile(String uri) {
        return openFile(URI.create(uri));
    }

    /**
     * Verify if an object exists on the given URI
     * @param uri of the wanted object
     * @return if the file exists
     */
    boolean exists(URI uri);

    /**
     * Verify if an object exists on the given URI
     * @param uri of the wanted object
     * @return if the file exists
     */
    default boolean exists(String uri) {
        return exists(URI.create(uri));
    }

    /**
     * Store a given file in the storage
     * @param key file identifier
     * @param file {@link InputFile} containing meta data
     * @return URI of the stored file
     */
    URI uploadFile(String key, InputFile file);

    URI uploadFile(String location, InputFile file, String key);

    /**
     * Store a given file in the storage
     * @param inputFile {@link InputFile} containing meta data
     * @param keys a list of identifier which will be joined with an "/" to build the file path
     * @return URI of the stored file
     */
    default URI uploadFile(InputFile inputFile, String...keys){
        final String key = Stream.of(keys)
                .filter(Objects::nonNull)
                .map(k -> k.endsWith("/") ? k.substring(0, k.length() -1) : k)
                .collect(Collectors.joining("/"));
        return uploadFile(key, inputFile);
    }

    default URI uploadFile(String location, InputFile inputFile, String...keys) {
        final String key = Stream.of(keys)
                .filter(Objects::nonNull)
                .map(k -> k.endsWith("/") ? k.substring(0, k.length() -1) : k)
                .collect(Collectors.joining("/"));
        return uploadFile(location, inputFile, key);
    }

    /**
     * Download a given URI set from the storage and put the result in a zip
     * @param uriSet a Set of file URI to download
     * @return InputStream of the produced zip file
     */
    InputStream downloadFiles(Set<URI> uriSet);

    /**
     * Download a given directory URI from the storage and put all is sub-files and subdirectories in a zip
     * @param uri the URI of the directory to download
     * @return InputStream of the produced zip file
     */
    InputStream downloadDirectory(URI uri);
}
