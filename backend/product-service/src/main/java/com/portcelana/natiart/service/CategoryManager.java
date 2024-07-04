package com.portcelana.natiart.service;

import com.portcelana.natiart.dto.CategoryDto;
import com.portcelana.natiart.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryManager {
    Optional<Category> getCategory(String id);
    Category getCategoryOrDie(String id);
    List<Category> getCategories();
    Category createCategory(CategoryDto categoryDto);
    Category updateCategory(CategoryDto categoryDto);
    Category hideCategory(String id);
    void deleteCategory(String id);
}
