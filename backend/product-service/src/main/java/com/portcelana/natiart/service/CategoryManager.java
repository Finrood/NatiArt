package com.portcelana.natiart.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

import com.portcelana.natiart.dto.CategoryDto;
import com.portcelana.natiart.dto.PagedResponseDto;
import com.portcelana.natiart.model.Category;

public interface CategoryManager {
    Optional<Category> getCategory(String categoryId);

    Category getCategoryOrDie(String categoryId);

    List<Category> getCategories(Pageable pageable);

    /** Returns bounded items and page metadata; public discovery filters inactive entries before paging. */
    /** Returns a filtered page; inactive records are available only to authorized admin callers. */
    PagedResponseDto<CategoryDto> getCategoriesPage(Pageable pageable, boolean includeInactive);

    Category createCategory(CategoryDto categoryDto);

    Category updateCategory(CategoryDto categoryDto);

    Category inverseVisibility(String categoryId);

    void deleteCategory(String categoryId);
}
