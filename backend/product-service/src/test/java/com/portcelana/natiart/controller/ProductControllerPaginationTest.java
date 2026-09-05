package com.portcelana.natiart.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.service.ImageConversionService;
import com.portcelana.natiart.service.ProductManager;

@ExtendWith(MockitoExtension.class)
class ProductControllerPaginationTest {

    @Mock
    private ProductManager productManager;

    @Mock
    private ImageConversionService imageConversionService;

    @InjectMocks
    private ProductController productController;

    @Test
    void getProducts_clampsOversizedSizeToMax() {
        final Product product = new Product("label", new BigDecimal("10.00"));
        when(productManager.getProducts(any(Pageable.class))).thenReturn(List.of(product));

        productController.getProducts("user", 0, Integer.MAX_VALUE);

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productManager).getProducts(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void getNewProducts_clampsNegativePageToZero() {
        when(productManager.getNewProducts(any(Pageable.class))).thenReturn(List.of());

        productController.getNewProducts(-5, 20);

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productManager).getNewProducts(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    void getFeaturedProducts_clampsNonPositiveSizeToOne() {
        when(productManager.getFeaturedProducts(any(Pageable.class))).thenReturn(List.of());

        productController.getFeaturedProducts(1, 0);

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productManager).getFeaturedProducts(captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(1, captor.getValue().getPageSize());
    }

    @Test
    void getProducts_passesSanePagingThrough() {
        when(productManager.getProducts(any(Pageable.class))).thenReturn(List.of());

        productController.getProducts("user", 2, 10);

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productManager).getProducts(captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(10, captor.getValue().getPageSize());
    }
}
