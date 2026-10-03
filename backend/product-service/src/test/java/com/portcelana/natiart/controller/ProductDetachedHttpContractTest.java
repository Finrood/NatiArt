package com.portcelana.natiart.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.PackageRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.service.CategoryManager;
import com.portcelana.natiart.service.ImageConversionService;
import com.portcelana.natiart.service.ProductManager;

@DataJpaTest(
        properties = {
            "spring.sql.init.mode=never",
            "natiart.test.contract=ProductDetachedHttpContractTest",
            "spring.jpa.properties.hibernate.generate_statistics=true"
        })
class ProductDetachedHttpContractTest {
    @Autowired
    private ProductRepository products;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private PackageRepository packages;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void committedProductSerializesArraysAndRelationsAfterPersistenceSessionCloses() throws Exception {
        final TransactionTemplate transaction = new TransactionTemplate(transactions);
        final Product seeded = transaction.execute(status -> {
            final Category category = categories.save(new Category("category-" + UUID.randomUUID()));
            final Package packaging = packages.save(new Package("package-" + UUID.randomUUID(), 1, 2, 3));
            return products.save(new Product("Painted bowl", new BigDecimal("19.90"))
                    .setCategory(category)
                    .setPackaging(packaging)
                    .setStockQuantity(3)
                    .setTags(Set.of("handmade", "ceramic"))
                    .setImages(List.of("images/front.webp", "images/back.webp")));
        });
        assertNotNull(seeded);
        final Statistics sql = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        sql.clear();
        final Product detached = transaction.execute(
                status -> products.findByIdWithImages(seeded.getId()).orElseThrow());
        assertNotNull(detached);
        org.junit.jupiter.api.Assertions.assertEquals(1, sql.getPrepareStatementCount());
        org.junit.jupiter.api.Assertions.assertEquals(0, sql.getEntityFetchCount());
        org.junit.jupiter.api.Assertions.assertEquals(0, sql.getCollectionFetchCount());
        final ProductManager manager = mock(ProductManager.class);
        when(manager.getActiveProductWithImagesOrDie(seeded.getId())).thenReturn(detached);
        final MockMvc http = MockMvcBuilders.standaloneSetup(
                        new ProductController(manager, mock(CategoryManager.class), mock(ImageConversionService.class)))
                .build();
        http.perform(get("/products/" + seeded.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(seeded.getId()))
                .andExpect(jsonPath("$.categoryId")
                        .value(seeded.getCategory().orElseThrow().getId()))
                .andExpect(jsonPath("$.packageId")
                        .value(seeded.getPackaging().orElseThrow().getId()))
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.tags", containsInAnyOrder("handmade", "ceramic")))
                .andExpect(jsonPath("$.images").isArray())
                .andExpect(jsonPath("$.images", containsInAnyOrder("images/front.webp", "images/back.webp")));
    }
}
