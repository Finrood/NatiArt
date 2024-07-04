package com.portcelana.natiart.service;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.CategoryDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryManagerImpl implements CategoryManager{
    private final CategoryRepository categoryRepository;

    public CategoryManagerImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Optional<Category> getCategory(String id) {
        return categoryRepository.findById(id);
    }

    @Override
    public Category getCategoryOrDie(String id) {
        return getCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Category with id %s not found", id)));
    }

    @Override
    public List<Category> getCategories() {
        return categoryRepository.findAll();
    }

    @Override
    public Category createCategory(CategoryDto categoryDto) {
        if (categoryRepository.findCategoryByLabel(categoryDto.getLabel()).isPresent()) {
            throw new IllegalArgumentException(String.format("Category with label %s already exists", categoryDto.getLabel()));
        }
        final Category category = new Category(categoryDto.getLabel())
                .setDescription(categoryDto.getDescription());
        return categoryRepository.save(category);
    }

    @Override
    public Category updateCategory(CategoryDto categoryDto) {
        final Category category = getCategoryOrDie(categoryDto.getId());
        category.setLabel(categoryDto.getLabel());
        category.setDescription(categoryDto.getDescription());
        return categoryRepository.save(category);
    }

    //TODO Implements hidden/visible for model Category
    @Override
    public Category hideCategory(String id) {
        return null;
    }

    @Override
    public void deleteCategory(String id) {
        categoryRepository.deleteById(id);
    }
}
