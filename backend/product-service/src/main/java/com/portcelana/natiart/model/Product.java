package com.portcelana.natiart.model;

import com.portcelana.natiart.model.support.PersonalizationOption;
import com.portcelana.natiart.support.SetPersonalizationOptionJpaConverter;
import com.portcelana.natiart.support.SetStringJpaConverter;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class Product {
    @Id
    private String id;

    @Version
    private long version;

    @Column(nullable = false)
    private String label;

    private String description;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal originalPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal markedPrice;

    @Column(nullable = false)
    private int stockQuantity;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", referencedColumnName = "id")
    private Category category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "package_id", referencedColumnName = "id")
    private Package packaging;

    private Boolean hasFixedGoldenBorder;  // true = fixed with border, false = fixed without border, null = can be personalized

    @Column(length = 1000)
    @Convert(converter = SetPersonalizationOptionJpaConverter.class)
    private Set<PersonalizationOption> availablePersonalizations = new HashSet<>();

    @Column(length = 1000)
    @Convert(converter = SetStringJpaConverter.class)
    private Set<String> tags = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    private List<String> images = new ArrayList<>();

    @Column(nullable = false)
    private boolean newProduct;
    @Column(nullable = false)
    private boolean featuredProduct;

    @Column(nullable = false)
    private boolean active;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    protected Product() {
        // FOR JPA
    }

    public Product(String label, BigDecimal originalPrice) {
        this.id = UUID.randomUUID().toString();
        this.label = label;
        this.originalPrice = originalPrice;
        this.active = true;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public Product setLabel(String label) {
        this.label = label;
        return this;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public Product setDescription(String description) {
        this.description = description;
        return this;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public Product setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
        return this;
    }

    public Optional<BigDecimal> getMarkedPrice() {
        return Optional.ofNullable(markedPrice);
    }

    public Product setMarkedPrice(BigDecimal markedPrice) {
        this.markedPrice = markedPrice;
        return this;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public Product setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
        return this;
    }

    public Optional<Category> getCategory() {
        return Optional.ofNullable(category);
    }

    public Product setCategory(Category category) {
        this.category = category;
        return this;
    }

    public Optional<Package> getPackaging() {
        return Optional.ofNullable(packaging);
    }

    public Product setPackaging(Package packaging) {
        this.packaging = packaging;
        return this;
    }

    public Optional<Boolean> getHasFixedGoldenBorder() {
        return Optional.ofNullable(hasFixedGoldenBorder);
    }

    public Product setHasFixedGoldenBorder(Boolean hasFixedGoldenBorder) {
        this.hasFixedGoldenBorder = hasFixedGoldenBorder;
        return this;
    }

    public Set<PersonalizationOption> getAvailablePersonalizations() {
        return availablePersonalizations;
    }

    public Product setAvailablePersonalizations(Set<PersonalizationOption> availablePersonalizations) {
        this.availablePersonalizations = availablePersonalizations;
        return this;
    }

    public Set<String> getTags() {
        return tags;
    }

    public Product setTags(Set<String> tags) {
        this.tags = tags;
        return this;
    }

    public List<String> getImages() {
        return images;
    }

    public Product setImages(List<String> images) {
        this.images = images;
        return this;
    }

    public boolean isNewProduct() {
        return newProduct;
    }

    public Product setNewProduct(boolean newProduct) {
        this.newProduct = newProduct;
        return this;
    }

    public boolean isFeaturedProduct() {
        return featuredProduct;
    }

    public Product setFeaturedProduct(boolean featuredProduct) {
        this.featuredProduct = featuredProduct;
        return this;
    }

    public boolean isActive() {
        return active;
    }

    public Product setActive(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Product product = (Product) o;
        return Objects.equals(id, product.id);
    }
}
