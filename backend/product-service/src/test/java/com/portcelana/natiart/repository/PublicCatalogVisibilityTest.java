package com.portcelana.natiart.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.service.CategoryManager;
import com.portcelana.natiart.service.CategoryManagerImpl;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import(CategoryManagerImpl.class)
class PublicCatalogVisibilityTest {
    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryManager categoryManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void hidingCategoryRemovesItsProductsBeforePaginationAndRestoringItRevealsThem() {
        final Category visibleCategory = categoryRepository.save(new Category("A visible"));
        final Category hiddenCategory = categoryRepository.save(new Category("B hidden"));
        final Product visible = productRepository.save(product("A visible", visibleCategory));
        final Product hiddenByCategory = productRepository.save(product("B hidden", hiddenCategory));
        final Product hiddenIndividually =
                productRepository.save(product("C hidden", visibleCategory).setActive(false));
        entityManager.flush();
        entityManager.clear();

        categoryManager.inverseVisibility(hiddenCategory.getId());
        entityManager.flush();
        entityManager.clear();

        final PageRequest first = PageRequest.of(0, 1, Sort.by("label"));
        final PageRequest second = PageRequest.of(1, 1, Sort.by("label"));
        assertEquals(
                List.of(visibleCategory.getId()),
                categoryManager.getActiveCategories(first).stream()
                        .map(Category::getId)
                        .toList());
        assertTrue(categoryManager.getActiveCategories(second).isEmpty());
        assertEquals(
                hiddenCategory.getId(),
                categoryManager.getCategories(second).getFirst().getId());
        assertThrows(
                ResourceNotFoundException.class, () -> categoryManager.getActiveCategoryOrDie(hiddenCategory.getId()));
        assertEquals(
                hiddenCategory.getId(),
                categoryManager.getCategoryOrDie(hiddenCategory.getId()).getId());

        assertEquals(
                List.of(visible.getId()),
                productRepository.findAllActiveIds(first).getContent());
        assertTrue(productRepository.findAllActiveIds(second).isEmpty());
        assertEquals(
                List.of(visible.getId()),
                productRepository.findAllActiveIdsByNewProduct(true, first).getContent());
        assertEquals(
                List.of(visible.getId()),
                productRepository.findAllActiveIdsByFeaturedProduct(true, first).getContent());
        assertTrue(productRepository
                .findAllActiveIdsByCategory(hiddenCategory, first)
                .isEmpty());
        assertTrue(productRepository
                .findActiveByIdWithImages(hiddenByCategory.getId())
                .isEmpty());
        assertTrue(productRepository
                .findActiveByIdWithImages(hiddenIndividually.getId())
                .isEmpty());
        assertTrue(
                productRepository.findByIdWithImages(hiddenByCategory.getId()).isPresent());
        assertEquals(
                List.of(visible.getId()),
                productRepository
                        .findAllActiveWithImagesByIds(
                                List.of(visible.getId(), hiddenByCategory.getId(), hiddenIndividually.getId()))
                        .stream()
                        .map(Product::getId)
                        .toList());
        assertEquals(0, productRepository.decreaseStockIfAvailable(hiddenByCategory.getId(), 1));
        assertEquals(1, productRepository.decreaseStockIfAvailable(visible.getId(), 1));
        assertEquals(3, productRepository.findAllIds(PageRequest.of(0, 10)).getTotalElements());

        categoryManager.inverseVisibility(hiddenCategory.getId());
        entityManager.flush();
        entityManager.clear();

        assertEquals(
                hiddenCategory.getId(),
                categoryManager.getActiveCategoryOrDie(hiddenCategory.getId()).getId());
        assertFalse(productRepository
                .findActiveByIdWithImages(hiddenByCategory.getId())
                .isEmpty());
    }

    private static Product product(String label, Category category) {
        return new Product(label, BigDecimal.TEN)
                .setCategory(category)
                .setStockQuantity(2)
                .setNewProduct(true)
                .setFeaturedProduct(true);
    }
}
