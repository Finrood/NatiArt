package com.portcelana.natiart.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.portcelana.natiart.dto.product.PublicProductMetadataDto;
import com.portcelana.natiart.service.PublicProductMetadataManager;
import com.portcelana.natiart.service.StorefrontLanguage;

@RestController
public class PublicProductMetadataController {
    private final PublicProductMetadataManager metadataManager;

    public PublicProductMetadataController(PublicProductMetadataManager metadataManager) {
        this.metadataManager = metadataManager;
    }

    @GetMapping("/products/{productId}/metadata")
    public ResponseEntity<Void> getMetadata(
            @PathVariable String productId, @RequestParam(defaultValue = "en") String language) {
        final PublicProductMetadataDto metadata =
                metadataManager.getMetadata(productId, StorefrontLanguage.fromCode(language));
        return ResponseEntity.noContent()
                .header("Cache-Control", "no-store")
                .header("X-Natiart-Title", metadata.title())
                .header("X-Natiart-Description", metadata.description())
                .header("X-Natiart-Language", metadata.language())
                .header("X-Natiart-Type", metadata.type())
                .build();
    }
}
