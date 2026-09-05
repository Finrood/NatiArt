package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.storage.InputFile;
import com.portcelana.natiart.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class ProductManagerImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryManager categoryManager;

    @Mock
    private PackageManager packageManager;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private ProductManagerImpl productManager;

    @Test
    void createProduct_nullLabel_throwsWithoutSaving() {
        final ProductDto dto = new ProductDto(null, BigDecimal.TEN).setCategoryId("cat-1");

        assertThrows(IllegalArgumentException.class, () -> productManager.createProduct(dto, null));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_nullPrice_throwsWithoutSaving() {
        final ProductDto dto = new ProductDto("Mug", null).setCategoryId("cat-1");

        assertThrows(IllegalArgumentException.class, () -> productManager.createProduct(dto, null));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_nullImageLists_persistsWithEmptyImages() {
        final Category category = new Category("Tableware");
        when(categoryManager.getCategoryOrDie("cat-1")).thenReturn(category);
        when(packageManager.getPackage(null)).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        final ProductDto dto =
                new ProductDto("  Mug  ", BigDecimal.TEN).setCategoryId("cat-1").setImages(null);

        final Product created = productManager.createProduct(dto, null);

        assertEquals("Mug", created.getLabel());
        assertTrue(created.getImages().isEmpty());
        verify(storageService, never()).uploadFile(any(String.class), any(InputFile.class), any(String.class));
    }
}
