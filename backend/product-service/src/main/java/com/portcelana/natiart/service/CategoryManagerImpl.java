package com.portcelana.natiart.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.CategoryDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.ProductRepository;

@Service
public class CategoryManagerImpl implements CategoryManager {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryManagerImpl(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> getCategory(String categoryId) {
        return categoryRepository.findById(categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public Category getCategoryOrDie(String categoryId) {
        return getCategory(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id " + categoryId + " not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getCategories(Pageable pageable) {
        return categoryRepository.findAll(pageable).stream().toList();
    }

    @Override
    @Transactional
    public Category createCategory(CategoryDto categoryDto) {
        final String label = requireNonBlankLabel(categoryDto.getLabel());
        if (categoryRepository.findCategoryByLabel(label).isPresent()) {
            throw new IllegalArgumentException("Category with label [" + label + "] already exists");
        }

        final Category category = new Category(label).setDescription(categoryDto.getDescription());
        return categoryRepository.save(category);
    }

    @Override
    @Transactional
    public Category updateCategory(CategoryDto categoryDto) {
        final String label = requireNonBlankLabel(categoryDto.getLabel());
        final Category category = getCategoryOrDie(categoryDto.getId());
        final Optional<Category> clash = categoryRepository.findCategoryByLabel(label);
        if (clash.isPresent() && !clash.get().getId().equals(category.getId())) {
            throw new IllegalArgumentException("Category with label [" + label + "] already exists");
        }
        category.setLabel(label).setDescription(categoryDto.getDescription());
        return categoryRepository.save(category);
    }

    @Override
    @Transactional
    public Category inverseVisibility(String categoryId) {
        final Category category = getCategoryOrDie(categoryId);
        category.setActive(!category.isActive());
        return categoryRepository.save(category);
    }

    @Override
    @Transactional
    public void deleteCategory(String categoryId) {
        final Category category = getCategoryOrDie(categoryId);
        if (productRepository.existsByCategory(category)) {
            throw new IllegalArgumentException("Category with label [" + category.getLabel() + "] contains products.");
        }
        categoryRepository.delete(category);
    }

    private static String requireNonBlankLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Category label must not be blank");
        }
        return label.trim();
    }
}
