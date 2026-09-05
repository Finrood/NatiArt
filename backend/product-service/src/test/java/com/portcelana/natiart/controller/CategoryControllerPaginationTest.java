package com.portcelana.natiart.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.service.CategoryManager;

@ExtendWith(MockitoExtension.class)
class CategoryControllerPaginationTest {

    @Mock
    private CategoryManager categoryManager;

    @InjectMocks
    private CategoryController categoryController;

    @Test
    void getCategories_clampsOversizedSizeToMax() {
        final Category category = new Category("label");
        when(categoryManager.getCategories(any(Pageable.class))).thenReturn(List.of(category));

        categoryController.getCategories(0, Integer.MAX_VALUE);

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(categoryManager).getCategories(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void getCategories_clampsNegativePageToZero() {
        when(categoryManager.getCategories(any(Pageable.class))).thenReturn(List.of());

        categoryController.getCategories(-3, 20);

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(categoryManager).getCategories(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
    }
}
