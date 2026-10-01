package com.portcelana.natiart.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerUploadService.class);
    static final long DEFAULT_MAX_UPLOAD_BYTES = 5_000_000L;
    private static final String STORAGE_PREFIX = "customer-uploads/";

    private final CustomerUploadRepository customerUploadRepository;
    private final ImageConversionService imageConversionService;
    private final StorageService storageService;
    private final long maxUploadBytes;
    private Duration uploadTtl = Duration.ofHours(24);

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

    @Value("${natiart.customer-upload.ttl-hours:24}")
    public void setUploadTtlHours(long hours) {
        if (hours <= 0) {
            throw new IllegalArgumentException("Customer upload lifetime must be positive");
        }
        this.uploadTtl = Duration.ofHours(hours);
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
        final URI expectedUri = URI.create("file:" + key);
        try (InputStream convertedStream = converted.getInputStream()) {
            final InputFile convertedFile =
                    new InputFile(convertedStream, "image/webp", converted.getOriginalFilename(), converted.getSize());
            final CustomerUpload upload =
                    CustomerUpload.pending(uploadId, ownerExternalId, expectedUri.toString(), converted.getSize());
            // This method is deliberately not transactional: the pending row commits before storage writes.
            customerUploadRepository.saveAndFlush(upload);
            try {
                final URI storedUri = storageService.uploadFile(key, convertedFile);
                if (!expectedUri.equals(storedUri)) {
                    throw new IllegalStateException("Artwork storage returned an unexpected location");
                }
                customerUploadRepository.saveAndFlush(upload.markReady());
            } catch (RuntimeException | Error failure) {
                discardFailedUpload(upload, expectedUri, failure);
                throw failure;
            }
            return new CustomerUploadResponse(uploadId);
        }
    }

    private void discardFailedUpload(CustomerUpload upload, URI expectedUri, Throwable failure) {
        try {
            storageService.deleteFile(expectedUri);
        } catch (RuntimeException | Error cleanupFailure) {
            // Keep the pending row so the scheduled cleanup can retry file deletion.
            failure.addSuppressed(cleanupFailure);
            return;
        }
        try {
            customerUploadRepository.deleteById(upload.getId());
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /** Expires artwork that was never claimed, including uploads interrupted between DB and file writes. */
    @Scheduled(fixedDelayString = "${natiart.customer-upload.cleanup-delay-millis:3600000}")
    @Transactional
    public void cleanupExpiredUploads() {
        final Instant cutoff = Instant.now().minus(uploadTtl);
        for (CustomerUpload upload : customerUploadRepository.findByConsumedAtIsNullAndCreatedAtBefore(cutoff)) {
            if (upload.getConsumedAt() != null || !upload.getCreatedAt().isBefore(cutoff)) {
                continue;
            }
            try {
                storageService.deleteFile(URI.create(upload.getStorageUri()));
            } catch (RuntimeException cleanupFailure) {
                LOGGER.warn("Could not expire customer upload [{}]; cleanup will retry", upload.getId());
                continue;
            }
            customerUploadRepository.delete(upload);
        }
    }

    /** Atomically claims an upload for an order belonging to the same account. */
    @Transactional
    public CustomerUpload claimForOrder(String uploadId, String ownerExternalId) {
        validateUploadId(uploadId, ownerExternalId);
        final CustomerUpload upload = customerUploadRepository
                .findByIdForUpdate(uploadId)
                .orElseThrow(() -> new UnusableCustomerUploadException(uploadId));
        requireClaimable(upload, ownerExternalId);
        upload.setConsumedAt(Instant.now());
        return customerUploadRepository.save(upload);
    }

    /** Checks artwork ownership before showing a quote; the order claims it later under a lock. */
    @Transactional(readOnly = true)
    public void requireClaimableForQuote(String uploadId, String ownerExternalId) {
        validateUploadId(uploadId, ownerExternalId);
        final CustomerUpload upload = customerUploadRepository
                .findById(uploadId)
                .orElseThrow(() -> new UnusableCustomerUploadException(uploadId));
        requireClaimable(upload, ownerExternalId);
    }

    private void validateUploadId(String uploadId, String ownerExternalId) {
        requireOwner(ownerExternalId);
        if (uploadId == null || uploadId.isBlank()) {
            throw new IllegalArgumentException("Custom artwork must reference an upload");
        }
        try {
            UUID.fromString(uploadId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Custom artwork upload is invalid");
        }
    }

    private void requireClaimable(CustomerUpload upload, String ownerExternalId) {
        if (!ownerExternalId.equals(upload.getOwnerExternalId())) {
            throw new ResourceNotFoundException("Custom artwork upload was not found");
        }
        if (upload.getConsumedAt() != null) {
            throw new UnusableCustomerUploadException(upload.getId());
        }
        if (upload.getReadyAt() == null) {
            throw new IllegalArgumentException("Custom artwork upload is not ready");
        }
        if (!upload.getCreatedAt().isAfter(Instant.now().minus(uploadTtl))) {
            throw new UnusableCustomerUploadException(upload.getId());
        }
    }

    private void requireOwner(String ownerExternalId) {
        if (ownerExternalId == null || ownerExternalId.isBlank()) {
            throw new IllegalArgumentException("Customer artwork requires an authenticated owner");
        }
    }
}
