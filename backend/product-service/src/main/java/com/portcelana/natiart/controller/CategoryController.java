package com.portcelana.natiart.controller;

import com.portcelana.natiart.dto.CategoryDto;
import com.portcelana.natiart.service.CategoryManager;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
public class CategoryController {
    final private CategoryManager categoryManager;

    public CategoryController(CategoryManager categoryManager) {
        this.categoryManager = categoryManager;
    }

    @GetMapping("/categories/{categoryId}")
    public CategoryDto getCategory(@PathVariable String categoryId) {
        return CategoryDto.from(categoryManager.getCategoryOrDie(categoryId));
    }

    @GetMapping("/categories")
    public List<CategoryDto> getCategories() {
        return categoryManager.getCategories().stream()
                .map(CategoryDto::from)
                .sorted(Comparator.comparing(CategoryDto::getLabel))
                .toList();
    }

    @PostMapping("/categories/create")
    public CategoryDto createCategory(@RequestBody CategoryDto categoryDto) {
        return CategoryDto.from(categoryManager.createCategory(categoryDto));
    }

    @PutMapping("/categories/{categoryId}")
    public CategoryDto updateCategory(@PathVariable String categoryId, @RequestBody CategoryDto categoryDto) {
        Assert.isTrue(categoryId.equals(categoryDto.getId()), "category ids are not equals !");

        return CategoryDto.from(categoryManager.updateCategory(categoryDto));
    }

    @PatchMapping("/categories/{categoryId}/visibility/inverse")
    public CategoryDto inverseCategoryVisibility(@PathVariable String categoryId) {
        return CategoryDto.from(categoryManager.inverseVisibility(categoryId));
    }

    @DeleteMapping("/categories/{categoryId}")
    public void deleteCategory(@PathVariable String categoryId) {
        categoryManager.deleteCategory(categoryId);
    }
}
