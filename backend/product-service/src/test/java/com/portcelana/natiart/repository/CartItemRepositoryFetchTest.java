package com.portcelana.natiart.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.portcelana.natiart.dto.CartItemDto;
import com.portcelana.natiart.model.CartItem;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Personalization;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.PersonalizationOption;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class CartItemRepositoryFetchTest {

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

    private Product newProductWithImage(String label) {
        Product product = new Product(label, new BigDecimal("10.00"));
        product.setCategory(category);
        product = productRepository.save(product);
        product.getImages().add("file:///img/" + label + ".webp");
        return productRepository.save(product);
    }

    private Statistics statistics() {
        final Statistics statistics = entityManager
                .getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }

    @Test
    void cartListingLoadsPlainLinesInASingleQuery() {
        cartItemRepository.save(new CartItem("jane", newProductWithImage("plain-a")));
        cartItemRepository.save(new CartItem("jane", newProductWithImage("plain-b")));
        // Flush first: clear() alone would discard the still-unflushed inserts.
        entityManager.flush();
        entityManager.clear();

        final Statistics statistics = statistics();
        statistics.clear();
        final List<CartItem> lines = cartItemRepository.findCartItemsByUsername("jane");
        final List<CartItemDto> dtos = lines.stream().map(CartItemDto::from).toList();

        assertEquals(2, dtos.size());
        for (CartItem line : lines) {
            assertTrue(Hibernate.isInitialized(line.getProduct()));
            assertTrue(Hibernate.isInitialized(line.getProduct().getImages()));
        }
        assertEquals(1, statistics.getQueryExecutionCount());
    }

    @Test
    void cartListingInitializesPersonalizationWithoutPerLineProductSelects() {
        final CartItem personalized = new CartItem("jane", newProductWithImage("personalized"));
        final Personalization personalization = new Personalization();
        personalization.setPersonalizationOptions(Map.of(PersonalizationOption.GOLDEN_BORDER, "yes"));
        personalized.setPersonalization(personalization);
        cartItemRepository.save(personalized);
        // Flush first: clear() alone would discard the still-unflushed inserts.
        entityManager.flush();
        entityManager.clear();

        final Statistics statistics = statistics();
        statistics.clear();
        final List<CartItem> lines = cartItemRepository.findCartItemsByUsername("jane");
        final List<CartItemDto> dtos = lines.stream().map(CartItemDto::from).toList();

        assertEquals(1, dtos.size());
        assertTrue(Hibernate.isInitialized(lines.get(0).getPersonalization()));
        // One listing query plus at most the lazy personalization-options map;
        // the per-line product and image selects must be gone.
        assertTrue(statistics.getQueryExecutionCount() <= 2);
    }

    @Test
    void cartAddResponseLoadsProductDetailsInOneFetchQuery() {
        final Product product = newProductWithImage("add-details");
        final CartItem cartItem = new CartItem("jane", product);
        cartItemRepository.save(cartItem);
        entityManager.flush();
        entityManager.clear();

        final Statistics statistics = statistics();
        statistics.clear();
        final CartItem loaded = cartItemRepository
                .findCartItemByUsernameAndProductWithDetails("jane", product.getId())
                .orElseThrow();
        CartItemDto.from(loaded);

        assertTrue(Hibernate.isInitialized(loaded.getProduct()));
        assertTrue(Hibernate.isInitialized(loaded.getProduct().getImages()));
        assertEquals(1, statistics.getQueryExecutionCount());
    }
}
