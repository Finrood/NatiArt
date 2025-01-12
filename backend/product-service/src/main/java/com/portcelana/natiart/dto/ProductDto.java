package com.portcelana.natiart.dto;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.PersonalizationOption;

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
    private String packageId;
    private Boolean hasFixedGoldenBorder;
    private Set<PersonalizationOption> availablePersonalizations = new HashSet<>();
    private Set<String> tags = new HashSet<>();
    private List<String> images = new ArrayList<>();
    private boolean newProduct;
    private boolean featuredProduct;
    private boolean active;

    public ProductDto(String label, BigDecimal originalPrice) {
        this.label = label;
        this.originalPrice = originalPrice;
    }

    public static ProductDto from(Product product) {
        if (product == null) return null;
        return new ProductDto(product.getLabel(), product.getOriginalPrice())
                .setId(product.getId())
                .setDescription(product.getDescription()
                        .orElse(null))
                .setMarkedPrice(product.getMarkedPrice()
                        .orElseGet(product::getOriginalPrice))
                .setStockQuantity(product.getStockQuantity())
                .setCategoryId(product.getCategory()
                        .map(Category::getId)
                        .orElse(null))
                .setPackageId(product.getPackaging()
                        .map(Package::getId)
                        .orElse(null))
                .setHasFixedGoldenBorder(product.getHasFixedGoldenBorder()
                        .orElse(null))
                .setAvailablePersonalizations(product.getAvailablePersonalizations())
                .setTags(product.getTags())
                .setImages(product.getImages())
                .setNewProduct(product.isNewProduct())
                .setFeaturedProduct(product.isFeaturedProduct())
                .setActive(product.isActive());
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

    public ProductDto setCategoryId(String categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public String getPackageId() {
        return packageId;
    }

    public ProductDto setPackageId(String packageId) {
        this.packageId = packageId;
        return this;
    }

    public Boolean getHasFixedGoldenBorder() {
        return hasFixedGoldenBorder;
    }

    public ProductDto setHasFixedGoldenBorder(Boolean hasFixedGoldenBorder) {
        this.hasFixedGoldenBorder = hasFixedGoldenBorder;
        return this;
    }

    public Set<PersonalizationOption> getAvailablePersonalizations() {
        return availablePersonalizations;
    }

    public ProductDto setAvailablePersonalizations(Set<PersonalizationOption> availablePersonalizations) {
        this.availablePersonalizations = availablePersonalizations;
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

    public boolean isNewProduct() {
        return newProduct;
    }

    public ProductDto setNewProduct(boolean newProduct) {
        this.newProduct = newProduct;
        return this;
    }

    public boolean isFeaturedProduct() {
        return featuredProduct;
    }

    public ProductDto setFeaturedProduct(boolean featuredProduct) {
        this.featuredProduct = featuredProduct;
        return this;
    }

    public boolean isActive() {
        return active;
    }

    public ProductDto setActive(boolean active) {
        this.active = active;
        return this;
    }
}
