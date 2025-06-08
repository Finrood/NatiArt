package com.portcelana.natiart.controller;

import com.portcelana.natiart.dto.CategoryDto;
import com.portcelana.natiart.service.CategoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CategoryController {
    public static Logger LOGGER = LoggerFactory.getLogger(CategoryController.class);

    final private CategoryManager categoryManager;

    public CategoryController(CategoryManager categoryManager) {
        this.categoryManager = categoryManager;
    }

    @GetMapping("/categories/{categoryId}")
    public CategoryDto getCategory(@PathVariable String categoryId) {
        LOGGER.info("Getting category with id [{}]", categoryId);

        return CategoryDto.from(categoryManager.getCategoryOrDie(categoryId));
    }

    @GetMapping("/categories")
    public List<CategoryDto> getCategories(@RequestParam(required = false, defaultValue = "0") int page,
                                           @RequestParam(required = false, defaultValue = "20") int size) {
        LOGGER.info("Getting all categories");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "label"));
        return categoryManager.getCategories(pageable).stream()
                .map(CategoryDto::from)
                .toList();
    }

    @PostMapping("/categories/create")
    public CategoryDto createCategory(@RequestBody CategoryDto categoryDto) {
        LOGGER.info("Creating new category with label [{}] description [{}] and active [{}]",
                categoryDto.getLabel(),
                categoryDto.getDescription(),
                categoryDto.isActive());

        return CategoryDto.from(categoryManager.createCategory(categoryDto));
    }

    @PutMapping("/categories/{categoryId}")
    public CategoryDto updateCategory(@PathVariable String categoryId, @RequestBody CategoryDto categoryDto) {
        Assert.isTrue(categoryId.equals(categoryDto.getId()), "category ids are not equals !");

        LOGGER.info("Updating category with id [{}] with new data of label [{}] description [{}] and active [{}]",
                categoryId,
                categoryDto.getLabel(),
                categoryDto.getDescription(),
                categoryDto.isActive());

        return CategoryDto.from(categoryManager.updateCategory(categoryDto));
    }

    @PatchMapping("/categories/{categoryId}/visibility/inverse")
    public CategoryDto inverseCategoryVisibility(@PathVariable String categoryId) {
        LOGGER.info("Inverting visibility of category with id [{}]", categoryId);

        return CategoryDto.from(categoryManager.inverseVisibility(categoryId));
    }

    @DeleteMapping("/categories/{categoryId}")
    public void deleteCategory(@PathVariable String categoryId) {
        LOGGER.info("Deleting category with id [{}]", categoryId);

        categoryManager.deleteCategory(categoryId);
    }
}
