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
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final Category updated = categoryManager.updateCategory(new CategoryDto("Box").setId(current.getId()));

        assertEquals("Box", updated.getLabel());
        verify(categoryRepository).save(current);
    }
}
