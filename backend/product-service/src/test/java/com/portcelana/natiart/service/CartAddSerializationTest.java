package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.portcelana.natiart.dto.CartItemDto;
import com.portcelana.natiart.model.CartItem;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Personalization;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.PersonalizationOption;
import com.portcelana.natiart.repository.CartItemRepository;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.storage.StorageService;

@DataJpaTest(properties = {"spring.sql.init.mode=never", "spring.jpa.open-in-view=false"})
@Import({CartManagerImpl.class, ProductManagerImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CartAddSerializationTest {
    @Autowired
    CartManagerImpl manager;

    @Autowired
    CartItemRepository carts;

    @Autowired
    ProductRepository products;

    @Autowired
    CategoryRepository categories;

    @Autowired
    PlatformTransactionManager transactions;

    @MockitoBean
    CategoryManager categoryManager;

    @MockitoBean
    PackageManager packageManager;

    @MockitoBean
    StorageService storage;

    @MockitoBean
    ProductImageLifecycle imageLifecycle;

    @org.junit.jupiter.api.Test
    void serializeNewLineAfterRealManagerTransaction() throws Exception {
        final String username = "new-line-" + UUID.randomUUID();
        final String id = new TransactionTemplate(transactions).execute(status -> {
            final Category category = categories.save(new Category("category-" + UUID.randomUUID()));
            return products.saveAndFlush(new Product("Art", BigDecimal.TEN)
                            .setCategory(category)
                            .setImages(List.of("file:///owned/cover.webp")))
                    .getId();
        });
        final CartItemDto response = manager.createCartItem(username, id);
        assertEquals(1, response.getQuantity());
        assertTrue(new ObjectMapper().writeValueAsString(response).contains("file:///owned/cover.webp"));
        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> assertEquals(
                        1,
                        carts.findCartItemByUsernameAndProductWithDetails(username, id)
                                .orElseThrow()
                                .getQuantity()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void serializeExistingLineAfterRealManagerTransaction(boolean personalized) throws Exception {
        final String id = new TransactionTemplate(transactions).execute(status -> {
            final Category category = categories.save(new Category("category-" + UUID.randomUUID()));
            final Product product = products.save(new Product("Art", BigDecimal.TEN)
                    .setCategory(category)
                    .setImages(List.of("file:///owned/cover.webp")));
            final CartItem line = new CartItem("boundary", product);
            if (personalized)
                line.setPersonalization(new Personalization()
                        .setPersonalizationOptions(Map.of(PersonalizationOption.GOLDEN_BORDER, "true")));
            carts.saveAndFlush(line);
            return product.getId();
        });
        final CartItemDto response = manager.createCartItem("boundary", id);
        assertEquals(2, response.getQuantity());
        final String json = new ObjectMapper().writeValueAsString(response);
        assertTrue(json.contains("file:///owned/cover.webp"));
        if (personalized) assertTrue(json.contains("GOLDEN_BORDER"));
        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> assertEquals(
                        2,
                        carts.findCartItemByUsernameAndProductWithDetails("boundary", id)
                                .orElseThrow()
                                .getQuantity()));
    }
}
