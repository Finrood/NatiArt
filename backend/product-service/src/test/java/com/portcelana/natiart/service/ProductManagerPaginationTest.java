package com.portcelana.natiart.service;

import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductManagerPaginationTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryManager categoryManager;

    @Mock
    private PackageManager packageManager;

    @Mock
    private com.portcelana.natiart.storage.StorageService storageService;

    private ProductManagerImpl productManager;

    @BeforeEach
    void setUp() {
        productManager = new ProductManagerImpl(productRepository, categoryManager, packageManager, storageService);
    }

    @Test
    void getProductsPreservesPageableOrderingRegardlessOfFetchOrder() {
        var pageable = PageRequest.of(0, 3, org.springframework.data.domain.Sort.by("label"));

        Product a = new Product("a", new BigDecimal("1"));
        Product b = new Product("b", new BigDecimal("1"));
        Product c = new Product("c", new BigDecimal("1"));
        List<String> orderedIds = List.of(a.getId(), b.getId(), c.getId());

        when(productRepository.findAllIds(pageable)).thenReturn(new PageImpl<>(orderedIds, pageable, 3));
        when(productRepository.findAllWithImagesByIds(anyList())).thenReturn(List.of(c, a, b));

        List<Product> result = productManager.getProducts(pageable);

        assertEquals(orderedIds, result.stream().map(Product::getId).toList());
    }
}
