package com.portcelana.natiart.service;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.CategoryDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.ProductRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
        return categoryRepository.findAll(pageable).stream()
                .toList();
    }

    @Override
    @Transactional
    public Category createCategory(CategoryDto categoryDto) {
        if (categoryRepository.findCategoryByLabel(categoryDto.getLabel()).isPresent()) {
            throw new IllegalArgumentException("Category with label [" + categoryDto.getLabel() + "] already exists");
        }

        final Category category = new Category(categoryDto.getLabel())
                .setDescription(categoryDto.getDescription());
        return categoryRepository.save(category);
    }

    @Override
    @Transactional
    public Category updateCategory(CategoryDto categoryDto) {
        final Category category = getCategoryOrDie(categoryDto.getId());
        category.setLabel(categoryDto.getLabel())
                .setDescription(categoryDto.getDescription());
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
}
