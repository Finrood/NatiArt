package com.portcelana.natiart.dto;

import com.portcelana.natiart.model.Package;

public class PackageDto {
    private String id;
    private String label;
    private float height;
    private float width;
    private float depth;
    private boolean active;

    public static PackageDto from(Package pack) {
        return new PackageDto()
                .setId(pack.getId())
                .setLabel(pack.getLabel())
                .setHeight(pack.getHeight())
                .setWidth(pack.getWidth())
                .setDepth(pack.getDepth())
                .setActive(pack.isActive());
    }

    public String getId() {
        return id;
    }

    public PackageDto setId(String id) {
        this.id = id;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public PackageDto setLabel(String label) {
        this.label = label;
        return this;
    }

    public float getHeight() {
        return height;
    }

    public PackageDto setHeight(float height) {
        this.height = height;
        return this;
    }

    public float getWidth() {
        return width;
    }

    public PackageDto setWidth(float width) {
        this.width = width;
        return this;
    }

    public float getDepth() {
        return depth;
    }

    public PackageDto setDepth(float depth) {
        this.depth = depth;
        return this;
    }

    public boolean isActive() {
        return active;
    }

    public PackageDto setActive(boolean active) {
        this.active = active;
        return this;
    }
}
