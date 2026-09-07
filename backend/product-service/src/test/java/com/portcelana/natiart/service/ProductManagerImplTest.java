package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
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
    void createProduct_negativeOriginalPrice_throwsWithoutSaving() {
        final ProductDto dto = new ProductDto("Mug", new BigDecimal("-0.01")).setCategoryId("cat-1");

        assertThrows(IllegalArgumentException.class, () -> productManager.createProduct(dto, null));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_negativeMarkedPrice_throwsWithoutSaving() {
        final ProductDto dto =
                new ProductDto("Mug", BigDecimal.TEN).setCategoryId("cat-1").setMarkedPrice(new BigDecimal("-5.00"));

        assertThrows(IllegalArgumentException.class, () -> productManager.createProduct(dto, null));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_negativeStock_throwsWithoutSaving() {
        final ProductDto dto =
                new ProductDto("Mug", BigDecimal.TEN).setCategoryId("cat-1").setStockQuantity(-1);

        assertThrows(IllegalArgumentException.class, () -> productManager.createProduct(dto, null));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_zeroPriceAndStock_persists() {
        final Category category = new Category("Tableware");
        when(categoryManager.getCategoryOrDie("cat-1")).thenReturn(category);
        when(packageManager.getPackage(null)).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        final ProductDto dto =
                new ProductDto("Mug", BigDecimal.ZERO).setCategoryId("cat-1").setStockQuantity(0);

        final Product created = productManager.createProduct(dto, null);

        assertEquals(BigDecimal.ZERO, created.getOriginalPrice());
        verify(productRepository, atLeastOnce()).save(any(Product.class));
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

    @Test
    void inverseVisibility_existingProduct_flipsAtomicallyWithoutReadModifyWrite() {
        final Product product = new Product("Mug", BigDecimal.TEN);
        when(productRepository.toggleActiveById(product.getId())).thenReturn(1);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        final Product toggled = productManager.inverseVisibility(product.getId());

        assertEquals(product.getId(), toggled.getId());
        verify(productRepository).toggleActiveById(product.getId());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void inverseVisibility_missingProduct_throwsNotFound() {
        when(productRepository.toggleActiveById("missing")).thenReturn(0);

        assertThrows(ResourceNotFoundException.class, () -> productManager.inverseVisibility("missing"));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void getProductOrDie_nullId_throwsNotFoundWithoutQuerying() {
        assertThrows(ResourceNotFoundException.class, () -> productManager.getProductOrDie(null));

        verify(productRepository, never()).findById(any());
    }

    @Test
    void getProductWithImages_nullId_returnsEmptyWithoutQuerying() {
        assertTrue(productManager.getProductWithImages(null).isEmpty());

        verify(productRepository, never()).findByIdWithImages(any());
    }

    @Test
    void deleteProduct_nullId_throwsNotFoundWithoutDeleting() {
        assertThrows(ResourceNotFoundException.class, () -> productManager.deleteProduct(null));

        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void getProductImage_malformedPath_throwsBadRequestWithoutTouchingStorage() {
        final IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> productManager.getProductImage("::bad::"));

        assertEquals("Invalid image path: ::bad::", thrown.getMessage());
        verify(storageService, never()).openFile(any(URI.class));
    }

    @Test
    void getProductsOrDie_allPresent_loadsInOneQuery() {
        final Product plate = new Product("Plate", BigDecimal.TEN);
        final Product mug = new Product("Mug", BigDecimal.TEN);
        when(productRepository.findAllById(List.of(plate.getId(), mug.getId()))).thenReturn(List.of(plate, mug));

        final Map<String, Product> result = productManager.getProductsOrDie(List.of(plate.getId(), mug.getId()));

        assertEquals(2, result.size());
        assertEquals(plate.getId(), result.get(plate.getId()).getId());
        verify(productRepository, times(1)).findAllById(anyList());
    }

    @Test
    void getProductsOrDie_missingId_throwsNotFound() {
        final Product plate = new Product("Plate", BigDecimal.TEN);
        when(productRepository.findAllById(List.of(plate.getId(), "missing"))).thenReturn(List.of(plate));

        assertThrows(
                ResourceNotFoundException.class,
                () -> productManager.getProductsOrDie(List.of(plate.getId(), "missing")));
    }
}
