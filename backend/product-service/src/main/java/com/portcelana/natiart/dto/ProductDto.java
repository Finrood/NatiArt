package com.portcelana.natiart.dto;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

    public class ProductDto {
        private String id;
        private String label;
        private String description;
        private BigDecimal originalPrice;
        private BigDecimal markedPrice;
        private int stockQuantity;
        private String categoryId;
        private Set<String> tags = new HashSet<>();
        private List<String> images = new ArrayList<>();

    public static ProductDto from(Product product) {
        if (product == null) return null;
        return new ProductDto(product.getLabel(), product.getOriginalPrice())
                .setId(product.getId())
                .setDescription(product.getDescription())
                .setMarkedPrice(product.getMarkedPrice())
                .setStockQuantity(product.getStockQuantity())
                .setCategory(product.getCategory().getId())
                .setTags(product.getTags())
                .setImages(product.getImages());
    }

    public ProductDto(String label, BigDecimal originalPrice) {
        this.label = label;
        this.originalPrice = originalPrice;
    }

    public String getId() {
        return id;
    }

    public ProductDto setId(String id) {
        this.id = id;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public ProductDto setLabel(String label) {
        this.label = label;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public ProductDto setDescription(String description) {
        this.description = description;
        return this;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public ProductDto setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
        return this;
    }

    public BigDecimal getMarkedPrice() {
        return markedPrice;
    }

    public ProductDto setMarkedPrice(BigDecimal markedPrice) {
        this.markedPrice = markedPrice;
        return this;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public ProductDto setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
        return this;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public ProductDto setCategory(String categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public Set<String> getTags() {
        return tags;
    }

    public ProductDto setTags(Set<String> tags) {
        this.tags = tags;
        return this;
    }

    public List<String> getImages() {
        return images;
    }

    public ProductDto setImages(List<String> images) {
        this.images = images;
        return this;
    }
}
