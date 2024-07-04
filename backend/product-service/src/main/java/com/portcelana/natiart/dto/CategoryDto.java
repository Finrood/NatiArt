package com.portcelana.natiart.dto;

import com.portcelana.natiart.model.Category;

public class CategoryDto {
    private String id;
    private String label;
    private String description;

    public static CategoryDto from(Category category) {
        if (category == null) return null;
        return new CategoryDto(category.getLabel())
                .setId(category.getId())
                .setDescription(category.getDescription());
    }

    public CategoryDto(String label) {
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public CategoryDto setId(String id) {
        this.id = id;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public CategoryDto setLabel(String label) {
        this.label = label;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public CategoryDto setDescription(String description) {
        this.description = description;
        return this;
    }

    @Override
    public String toString() {
        return "CategoryDto{" +
                "id='" + id + '\'' +
                ", label='" + label + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
