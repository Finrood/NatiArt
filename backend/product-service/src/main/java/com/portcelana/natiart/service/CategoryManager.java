package com.portcelana.natiart.service;

import com.portcelana.natiart.dto.CategoryDto;
import com.portcelana.natiart.model.Category;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CategoryManager {
    Optional<Category> getCategory(String categoryId);

    Category getCategoryOrDie(String categoryId);

    List<Category> getCategories(Pageable pageable);

    Category createCategory(CategoryDto categoryDto);

    Category updateCategory(CategoryDto categoryDto);

    Category inverseVisibility(String categoryId);

    void deleteCategory(String categoryId);
}
