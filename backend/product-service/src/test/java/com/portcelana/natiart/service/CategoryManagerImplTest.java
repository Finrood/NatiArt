package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.CategoryDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class CategoryManagerImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryManagerImpl categoryManager;

    @Test
    void createCategory_nullLabel_throwsWithoutSaving() {
        final CategoryDto dto = new CategoryDto(null);

        assertThrows(IllegalArgumentException.class, () -> categoryManager.createCategory(dto));

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createCategory_blankLabel_throwsWithoutSaving() {
        final CategoryDto dto = new CategoryDto("   ");

        assertThrows(IllegalArgumentException.class, () -> categoryManager.createCategory(dto));

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createCategory_paddedDuplicateLabel_throwsAgainstTrimmedLookup() {
        final Category existing = new Category("Box");
        when(categoryRepository.findCategoryByLabel("Box")).thenReturn(Optional.of(existing));

        final CategoryDto dto = new CategoryDto("  Box  ");

        assertThrows(IllegalArgumentException.class, () -> categoryManager.createCategory(dto));

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createCategory_freshLabel_persistsTrimmedLabel() {
        when(categoryRepository.findCategoryByLabel("Box")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Category created = categoryManager.createCategory(new CategoryDto("  Box  "));

        assertEquals("Box", created.getLabel());
    }

    @Test
    void updateCategory_duplicateOfAnotherCategory_throwsWithoutSaving() {
        final Category current = new Category("Old");
        final Category other = new Category("Box");
        when(categoryRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(categoryRepository.findCategoryByLabel("Box")).thenReturn(Optional.of(other));

        final CategoryDto dto = new CategoryDto("Box").setId(current.getId());

        assertThrows(IllegalArgumentException.class, () -> categoryManager.updateCategory(dto));

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updateCategory_ownLabel_persists() {
        final Category current = new Category("Box");
        when(categoryRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(categoryRepository.findCategoryByLabel("Box")).thenReturn(Optional.of(current));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Category updated = categoryManager.updateCategory(new CategoryDto("Box").setId(current.getId()));

        assertEquals("Box", updated.getLabel());
        verify(categoryRepository).save(current);
    }

    @Test
    void createCategory_concurrentDuplicate_mapsConstraintViolationToBadRequest() {
        when(categoryRepository.findCategoryByLabel("Box")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        final IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class, () -> categoryManager.createCategory(new CategoryDto("Box")));

        assertEquals("Category with label [Box] already exists", thrown.getMessage());
    }

    @Test
    void updateCategory_concurrentRename_mapsConstraintViolationToBadRequest() {
        final Category current = new Category("Old");
        when(categoryRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(categoryRepository.findCategoryByLabel("Box")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        final IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> categoryManager.updateCategory(new CategoryDto("Box").setId(current.getId())));

        assertEquals("Category with label [Box] already exists", thrown.getMessage());
    }

    @Test
    void inverseVisibility_existingCategory_flipsAtomicallyWithoutReadModifyWrite() {
        final Category category = new Category("Box");
        when(categoryRepository.toggleActiveById(category.getId())).thenReturn(1);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        final Category toggled = categoryManager.inverseVisibility(category.getId());

        assertEquals(category.getId(), toggled.getId());
        verify(categoryRepository).toggleActiveById(category.getId());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void inverseVisibility_missingCategory_throwsNotFound() {
        when(categoryRepository.toggleActiveById("missing")).thenReturn(0);

        assertThrows(ResourceNotFoundException.class, () -> categoryManager.inverseVisibility("missing"));

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void getCategoryOrDie_nullId_throwsNotFoundWithoutQuerying() {
        assertThrows(ResourceNotFoundException.class, () -> categoryManager.getCategoryOrDie(null));

        verify(categoryRepository, never()).findById(any());
    }
}
