package com.portcelana.natiart.service;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.CustomerUploadResponse;
import com.portcelana.natiart.model.CustomerUpload;
import com.portcelana.natiart.repository.CustomerUploadRepository;
import com.portcelana.natiart.storage.InputFile;
import com.portcelana.natiart.storage.StorageService;

/** Handles bounded, authenticated artwork uploads and one-time order claims. */
@Service
public class CustomerUploadService {
    static final long DEFAULT_MAX_UPLOAD_BYTES = 5_000_000L;
    private static final String STORAGE_PREFIX = "customer-uploads/";

    private final CustomerUploadRepository customerUploadRepository;
    private final ImageConversionService imageConversionService;
    private final StorageService storageService;
    private final long maxUploadBytes;

    public CustomerUploadService(
            CustomerUploadRepository customerUploadRepository,
            ImageConversionService imageConversionService,
            StorageService storageService,
            @Value("${natiart.customer-upload.max-bytes:" + DEFAULT_MAX_UPLOAD_BYTES + "}") long maxUploadBytes) {
        if (maxUploadBytes <= 0) {
            throw new IllegalArgumentException("Customer upload limit must be positive");
        }
        this.customerUploadRepository = customerUploadRepository;
        this.imageConversionService = imageConversionService;
        this.storageService = storageService;
        this.maxUploadBytes = maxUploadBytes;
    }

    /** Stores a converted image under a server-generated key and returns its opaque id. */
    public CustomerUploadResponse upload(String ownerExternalId, MultipartFile file) throws IOException {
        requireOwner(ownerExternalId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Artwork upload must not be empty");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new IllegalArgumentException("Artwork upload exceeds the allowed size");
        }
        final String contentType = file.getContentType();
        if (contentType == null
                || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Artwork upload must be an image");
        }

        final List<MultipartFile> convertedImages = imageConversionService.convertToWebP(List.of(file));
        if (convertedImages == null || convertedImages.size() != 1) {
            throw new IllegalArgumentException("Artwork upload could not be converted");
        }
        final MultipartFile converted = convertedImages.getFirst();
        if (converted == null || converted.isEmpty() || converted.getSize() > maxUploadBytes) {
            throw new IllegalArgumentException("Artwork upload exceeds the allowed size");
        }
        final String uploadId = UUID.randomUUID().toString();
        final String key = STORAGE_PREFIX + uploadId + ".webp";
        final URI storageUri = storageService.uploadFile(key, InputFile.from(converted));
        if (storageUri == null) {
            throw new IllegalStateException("Artwork storage did not return a location");
        }

        customerUploadRepository.save(new CustomerUpload(
                uploadId, ownerExternalId, storageUri.toString(), "image/webp", converted.getSize()));
        return new CustomerUploadResponse(uploadId);
    }

    /** Atomically claims an upload for an order belonging to the same account. */
    @Transactional
    public CustomerUpload claimForOrder(String uploadId, String ownerExternalId) {
        requireOwner(ownerExternalId);
        if (uploadId == null || uploadId.isBlank()) {
            throw new IllegalArgumentException("Custom artwork must reference an upload");
        }
        try {
            UUID.fromString(uploadId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Custom artwork upload is invalid");
        }

        final CustomerUpload upload = customerUploadRepository
                .findByIdForUpdate(uploadId)
                .orElseThrow(() -> new ResourceNotFoundException("Custom artwork upload was not found"));
        if (!ownerExternalId.equals(upload.getOwnerExternalId())) {
            throw new ResourceNotFoundException("Custom artwork upload was not found");
        }
        if (upload.getConsumedAt() != null) {
            throw new IllegalArgumentException("Custom artwork upload was already used");
        }
        upload.setConsumedAt(Instant.now());
        return customerUploadRepository.save(upload);
    }

    private void requireOwner(String ownerExternalId) {
        if (ownerExternalId == null || ownerExternalId.isBlank()) {
            throw new IllegalArgumentException("Customer artwork requires an authenticated owner");
        }
    }
}
