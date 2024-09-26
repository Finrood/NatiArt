package com.saas.directory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class Role {
    @Id
    private String id;

    @Version
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private RoleName label;
    private String description;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean active;

    private Instant deactivatedAt;

    protected Role() {
        // FOR JPA
    }

    public Role(RoleName label) {
        this.id = UUID.randomUUID().toString();
        this.label = label;
        this.active = true;
    }


    public String getId() {
        return id;
    }

    public RoleName getLabel() {
        return label;
    }

    public Role setLabel(RoleName label) {
        this.label = label;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Role setDescription(String description) {
        this.description = description;
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
        Role user = (Role) o;
        return Objects.equals(id, user.id);
    }
}
