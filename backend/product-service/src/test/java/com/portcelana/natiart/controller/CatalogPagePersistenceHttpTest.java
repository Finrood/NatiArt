package com.portcelana.natiart.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.PackageRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.service.CategoryManagerImpl;
import com.portcelana.natiart.service.ImageConversionService;
import com.portcelana.natiart.service.PackageManagerImpl;
import com.portcelana.natiart.service.ProductManagerImpl;
import com.portcelana.natiart.storage.StorageService;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import({ProductManagerImpl.class, CategoryManagerImpl.class, PackageManagerImpl.class})
class CatalogPagePersistenceHttpTest {
    @Autowired
    private ProductManagerImpl productManager;

    @Autowired
    private CategoryManagerImpl categoryManager;

    @Autowired
    private PackageManagerImpl packageManager;

    @Autowired
    private ProductRepository products;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private PackageRepository packages;

    @MockitoBean
    private StorageService storage;

    @Test
    void twentyFirstRowsAreReachableWithMetadataAndCategoryFilteringBeforeStablePaging() throws Exception {
        final List<Category> categoryRows = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            categoryRows.add(categories.save(new Category("Category-%02d".formatted(i))));
            packages.save(new Package("Package-%02d".formatted(i), 10, 10, 10));
        }
        final Category selected = categoryRows.getFirst();
        final List<String> productIds = new ArrayList<>();
        for (int i = 0; i < 21; i++)
            productIds.add(products.save(new Product("Same label", BigDecimal.TEN).setCategory(selected))
                    .getId());
        products.save(new Product("A different category", BigDecimal.ONE).setCategory(categoryRows.get(1)));
        products.flush();
        productIds.sort(Comparator.naturalOrder());
        final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ProductController(productManager, categoryManager, new ImageConversionService()),
                        new CategoryController(categoryManager),
                        new PackageController(packageManager))
                .build();
        mvc.perform(get("/products/page").param("categoryId", selected.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(21))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].id").value(productIds.getFirst()));
        mvc.perform(get("/products/page").param("categoryId", selected.getId()).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(productIds.getLast()));
        mvc.perform(get("/products/page").param("categoryId", selected.getId()).param("query", "different"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/categories/page").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(21))
                .andExpect(jsonPath("$.items[0].label").value("Category-20"));
        mvc.perform(get("/packages/page").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(21))
                .andExpect(jsonPath("$.items[0].label").value("Package-20"));
    }
}
