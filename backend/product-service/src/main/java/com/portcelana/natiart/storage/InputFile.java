package com.portcelana.natiart.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public record InputFile(InputStream inputStream, String contentType, String filename, long size) {

    public static InputFile of(File file, String contentType) throws IOException {
        return new InputFile(
                new FileInputStream(file),
                contentType,
                file.getName(),
                file.length()
        );
    }

    public static InputFile from(MultipartFile file) throws IOException {
        return new InputFile(
                file.getInputStream(),
                file.getContentType(),
                file.getOriginalFilename(),
                file.getSize()
        );
    }

}
