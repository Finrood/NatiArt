package com.portcelana.natiart.service;

import com.portcelana.natiart.dto.CategoryDto;
import com.portcelana.natiart.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryManager {
    Optional<Category> getCategory(String categoryId);
    Category getCategoryOrDie(String categoryId);
    List<Category> getCategories();
    Category createCategory(CategoryDto categoryDto);
    Category updateCategory(CategoryDto categoryDto);
    Category inverseVisibility(String categoryId);
    void deleteCategory(String categoryId);
}
