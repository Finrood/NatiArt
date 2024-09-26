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
public class Package {
    @OneToMany(mappedBy = "packaging", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<Product> products = new HashSet<>();
    @Id
    private String id;
    @Version
    private long version;
    @Column(nullable = false, unique = true)
    private String label;
    @Column(nullable = false)
    private float height;
    @Column(nullable = false)
    private float width;
    @Column(nullable = false)
    private float depth;
    @Column(nullable = false)
    private boolean active;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    protected Package() {
    }

    public Package(String label, float height, float width, float depth) {
        this.id = UUID.randomUUID().toString();
        this.label = label;
        this.height = height;
        this.width = width;
        this.depth = depth;
        this.active = true;
    }

    public String getId() {
        return id;
    }

    public Package setId(String id) {
        this.id = id;
        return this;
    }

    public long getVersion() {
        return version;
    }

    public Package setVersion(long version) {
        this.version = version;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public Package setLabel(String label) {
        this.label = label;
        return this;
    }

    public float getHeight() {
        return height;
    }

    public Package setHeight(float height) {
        this.height = height;
        return this;
    }

    public float getWidth() {
        return width;
    }

    public Package setWidth(float width) {
        this.width = width;
        return this;
    }

    public float getDepth() {
        return depth;
    }

    public Package setDepth(float depth) {
        this.depth = depth;
        return this;
    }

    public Set<Product> getProducts() {
        return products;
    }

    public boolean isActive() {
        return active;
    }

    public Package setActive(boolean active) {
        this.active = active;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Package setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Package setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
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
        final Package category = (Package) o;
        return Objects.equals(id, category.id);
    }
}