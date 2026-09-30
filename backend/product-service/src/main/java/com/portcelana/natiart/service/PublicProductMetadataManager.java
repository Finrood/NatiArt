package com.portcelana.natiart.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.dto.product.PublicProductMetadataDto;
import com.portcelana.natiart.dto.product.PublicProductTextDto;
import com.portcelana.natiart.repository.ProductRepository;

@Service
public class PublicProductMetadataManager {
    private final ProductRepository productRepository;
    private String shopName = "NatiArt";

    public PublicProductMetadataManager(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Value("${natiart.storefront.name:NatiArt}")
    public void setShopName(String shopName) {
        if (shopName == null
                || shopName.isBlank()
                || shopName.length() > 80
                || shopName.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("A bounded storefront name is required");
        }
        this.shopName = shopName.trim();
    }

    /** Public scalar text only; inactive/missing products share a generic response. */
    @Transactional(readOnly = true)
    public PublicProductMetadataDto getMetadata(String productId, StorefrontLanguage language) {
        final PublicProductTextDto product = productId != null && productId.matches("[A-Za-z0-9-]{1,64}")
                ? productRepository.findActivePublicTextById(productId).orElse(null)
                : null;
        if (product == null) {
            return PublicProductMetadataDto.from(
                    shopName + " | " + language.shopTitle(),
                    language.shopDescription(shopName),
                    language.code(),
                    "website");
        }
        final String label = normalize(product.label(), 100);
        final String description =
                product.description() == null || product.description().isBlank()
                        ? label + " | " + shopName
                        : normalize(product.description(), 160);
        return PublicProductMetadataDto.from(label + " | " + shopName, description, language.code(), "product");
    }

    private static String normalize(String value, int limit) {
        final String normalized = value.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        final int length = Math.min(limit, normalized.codePointCount(0, normalized.length()));
        return normalized.substring(0, normalized.offsetByCodePoints(0, length));
    }
}
