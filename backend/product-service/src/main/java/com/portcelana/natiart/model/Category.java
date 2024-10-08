package com.portcelana.natiart.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class Category {
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<Product> products = new HashSet<>();
    @Id
    private String id;
    @Version
    private long version;
    @Column(nullable = false, unique = true)
    private String label;
    private String description;
    @Column(nullable = false)
    private boolean active;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    protected Category() {
    }

    public Category(String label) {
        this.id = UUID.randomUUID().toString();
        this.label = label;
        this.active = true;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public Category setLabel(String label) {
        this.label = label;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Category setDescription(String description) {
        this.description = description;
        return this;
    }

    public Set<Product> getProducts() {
        return products;
    }

    public boolean isActive() {
        return active;
    }

    public Category setActive(boolean active) {
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
        final Category category = (Category) o;
        return Objects.equals(id, category.id);
    }
}