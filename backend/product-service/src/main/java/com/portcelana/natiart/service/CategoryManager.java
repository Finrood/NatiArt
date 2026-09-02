package com.portcelana.natiart.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

import com.portcelana.natiart.dto.CategoryDto;
import com.portcelana.natiart.model.Category;

public interface CategoryManager {
    Optional<Category> getCategory(String categoryId);

    Category getCategoryOrDie(String categoryId);

    List<Category> getCategories(Pageable pageable);

    Category createCategory(CategoryDto categoryDto);

    Category updateCategory(CategoryDto categoryDto);

    Category inverseVisibility(String categoryId);

    void deleteCategory(String categoryId);
}
