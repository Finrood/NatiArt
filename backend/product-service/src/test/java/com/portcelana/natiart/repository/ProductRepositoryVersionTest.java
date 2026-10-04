package com.portcelana.natiart.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class ProductRepositoryVersionTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    private Category category;

    @BeforeEach
    void seedCategory() {
        category = categoryRepository.save(new Category("cat-" + System.nanoTime()));
    }

    @Test
    void stockReservationUpdatesVersionAndClearsManagedProduct() {
        final Product product = saveProduct("reserve", 3);
        final long initialVersion = product.getVersion();
        final Product managedBeforeBulkUpdate =
                productRepository.findById(product.getId()).orElseThrow();

        assertEquals(1, productRepository.decreaseStockIfAvailable(product.getId(), 2));
        assertFalse(entityManager.contains(managedBeforeBulkUpdate));

        final Product refreshed = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(1, refreshed.getStockQuantity());
        assertEquals(initialVersion + 1, refreshed.getVersion());
    }

    @Test
    void stockReservationCannotReserveInactiveProduct() {
        final Product product = saveProduct("inactive", 3).setActive(false);
        productRepository.saveAndFlush(product);
        entityManager.clear();
        final long initialVersion =
                productRepository.findById(product.getId()).orElseThrow().getVersion();

        assertEquals(0, productRepository.decreaseStockIfAvailable(product.getId(), 1));

        final Product unchanged = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(3, unchanged.getStockQuantity());
        assertEquals(initialVersion, unchanged.getVersion());
    }

    @Test
    void repeatedReservationsCannotOversellAndOnlySuccessfulUpdateAdvancesVersion() {
        final Product product = saveProduct("bounded", 1);
        final long initialVersion = product.getVersion();

        assertEquals(1, productRepository.decreaseStockIfAvailable(product.getId(), 1));
        assertEquals(0, productRepository.decreaseStockIfAvailable(product.getId(), 1));

        final Product refreshed = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(0, refreshed.getStockQuantity());
        assertEquals(initialVersion + 1, refreshed.getVersion());
    }

    @Test
    void visibilityToggleUpdatesVersionAndRefreshesProduct() {
        final Product product = saveProduct("toggle", 3);
        final long initialVersion = product.getVersion();

        assertEquals(1, productRepository.toggleActiveById(product.getId()));

        final Product refreshed = productRepository.findById(product.getId()).orElseThrow();
        assertFalse(refreshed.isActive());
        assertEquals(initialVersion + 1, refreshed.getVersion());
    }

    @Test
    void staleAdminSaveFailsAfterStockReservation() {
        final Product product = saveProduct("stale-admin", 3);
        entityManager.clear();
        final Product staleAdminSnapshot =
                productRepository.findById(product.getId()).orElseThrow();

        assertEquals(1, productRepository.decreaseStockIfAvailable(product.getId(), 1));

        staleAdminSnapshot.setLabel("stale overwrite");
        assertThrows(OptimisticLockingFailureException.class, () -> productRepository.saveAndFlush(staleAdminSnapshot));
    }

    @Test
    void staleAdminSaveFailsAfterVisibilityToggle() {
        final Product product = saveProduct("stale-visibility", 3);
        entityManager.clear();
        final Product staleAdminSnapshot =
                productRepository.findById(product.getId()).orElseThrow();

        assertEquals(1, productRepository.toggleActiveById(product.getId()));

        staleAdminSnapshot.setLabel("stale overwrite");
        assertThrows(OptimisticLockingFailureException.class, () -> productRepository.saveAndFlush(staleAdminSnapshot));
    }

    private Product saveProduct(String label, int stock) {
        final Product product = new Product(label, new BigDecimal("10.00"))
                .setCategory(category)
                .setStockQuantity(stock);
        productRepository.saveAndFlush(product);
        entityManager.clear();
        return productRepository.findById(product.getId()).orElseThrow();
    }
}
