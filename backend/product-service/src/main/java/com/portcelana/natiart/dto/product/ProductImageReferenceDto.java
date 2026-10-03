package com.portcelana.natiart.dto.product;

/** One ordered image reference: an owned existing URI or the ID of a multipart upload. */
public record ProductImageReferenceDto(String existingImage, String uploadId) {}
