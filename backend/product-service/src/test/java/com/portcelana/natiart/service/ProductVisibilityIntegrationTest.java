package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.ProductController;
import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.PackageRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.storage.StorageService;

@DataJpaTest(properties = {"spring.sql.init.mode=never", "spring.jpa.open-in-view=false"})
@Import(ProductManagerImpl.class)
class ProductVisibilityIntegrationTest {
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private ProductManagerImpl productManager;

    @MockitoBean
    private CategoryManager categoryManager;

    @MockitoBean
    private PackageManager packageManager;

    @MockitoBean
    private StorageService storageService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void toggleMapsCompleteDetachedResponseAndCommitsVisibility() {
        final Product saved = seedProduct();
        final ProductController controller =
                new ProductController(productManager, mock(CategoryManager.class), mock(ImageConversionService.class));

        final ProductDto response = controller.inverseProductVisibility(saved.getId());

        assertEquals(saved.getId(), response.getId());
        assertFalse(response.isActive());
        assertEquals(saved.getCategory().orElseThrow().getId(), response.getCategoryId());
        assertEquals(saved.getPackaging().orElseThrow().getId(), response.getPackageId());
        assertEquals(List.of("file:///tmp/product-images/one.webp"), response.getImages());
        assertFalse(productRepository.findById(saved.getId()).orElseThrow().isActive());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staleProductSaveCannotUndoVisibilityToggle() {
        final Product saved = seedProduct();
        final Product stale = productRepository.findById(saved.getId()).orElseThrow();

        productManager.inverseVisibility(saved.getId());
        stale.setLabel("stale update");

        assertThrows(OptimisticLockingFailureException.class, () -> productRepository.saveAndFlush(stale));
        assertFalse(productRepository.findById(saved.getId()).orElseThrow().isActive());
    }

    private Product seedProduct() {
        final Category category = categoryRepository.save(new Category("category-" + UUID.randomUUID()));
        final Package packaging = packageRepository.save(new Package("package-" + UUID.randomUUID(), 10, 10, 10));
        final Product product = new Product("Mug", new BigDecimal("10.00"))
                .setCategory(category)
                .setPackaging(packaging)
                .setImages(new ArrayList<>(List.of("file:///tmp/product-images/one.webp")));
        return productRepository.save(product);
    }
}
