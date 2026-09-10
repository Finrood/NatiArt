package com.portcelana.natiart.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.portcelana.natiart.model.CartItem;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Personalization;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.PersonalizationOption;

/**
 * Pins the deletion contract of {@link CartItem#getPersonalization()}
 * (cascade ALL, orphanRemoval): every cart delete path must take the line's
 * Personalization row and its option rows with it, so a cleared personalized
 * line cannot leak user-supplied personalization text behind.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
class CartItemCascadeSemanticsTest {

    @Autowired
    private CartItemRepository cartItemRepository;

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

    private CartItem seedPersonalizedLine(String username, String label) {
        final Product product = new Product(label, new BigDecimal("10.00"));
        product.setCategory(category);
        productRepository.save(product);
        final Personalization personalization = new Personalization();
        personalization.setPersonalizationOptions(Map.of(PersonalizationOption.GOLDEN_BORDER, "yes"));
        return cartItemRepository.save(new CartItem(username, product).setPersonalization(personalization));
    }

    private long personalizationCount() {
        return entityManager
                .createQuery("SELECT COUNT(p) FROM Personalization p", Long.class)
                .getSingleResult();
    }

    private long cartItemCount() {
        return entityManager
                .createQuery("SELECT COUNT(c) FROM CartItem c", Long.class)
                .getSingleResult();
    }

    @Test
    void bulkDeleteByUsernameKeepsThePersonalizationCascade() {
        seedPersonalizedLine("jane", "personalized-a");
        cartItemRepository.save(new CartItem(
                "jane", productRepository.save(new Product("plain-b", new BigDecimal("5.00")).setCategory(category))));
        // Flush first: clear() alone would discard the still-unflushed inserts.
        entityManager.flush();
        entityManager.clear();

        cartItemRepository.deleteByUsername("jane");
        entityManager.flush();
        entityManager.clear();

        assertEquals(0, cartItemCount());
        assertEquals(0, personalizationCount());
    }

    @Test
    void singleLineDeleteByUsernameAndProductKeepsThePersonalizationCascade() {
        final CartItem line = seedPersonalizedLine("jane", "personalized-a");
        entityManager.flush();
        entityManager.clear();

        cartItemRepository.deleteByUsernameAndProduct("jane", line.getProduct());
        entityManager.flush();
        entityManager.clear();

        assertEquals(0, cartItemCount());
        assertEquals(0, personalizationCount());
    }
}
