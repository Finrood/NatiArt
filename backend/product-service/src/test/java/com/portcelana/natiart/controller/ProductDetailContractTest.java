package com.portcelana.natiart.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.PackageRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.service.CategoryManager;
import com.portcelana.natiart.service.ImageConversionService;
import com.portcelana.natiart.service.ProductManager;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class ProductDetailContractTest {
    @Autowired
    private ProductRepository products;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private PackageRepository packages;

    @Autowired
    private EntityManager entityManager;

    @Test
    void detachedProductFetchSerializesReadableLabelsAndJsonTagArray() throws Exception {
        final Category category = categories.save(new Category("Porcelain artwork"));
        final Package packaging = packages.save(new Package("Gift box", 10, 10, 10));
        final Product saved = products.save(new Product("Painted vase", BigDecimal.TEN)
                .setCategory(category)
                .setPackaging(packaging)
                .setTags(Set.of("Hand painted"))
                .setImages(List.of("first-image", "second-image")));
        entityManager.flush();
        entityManager.clear();
        final Product detached = products.findByIdWithImages(saved.getId()).orElseThrow();
        entityManager.clear();
        final ProductManager manager = mock(ProductManager.class);
        when(manager.getActiveProductWithImagesOrDie(saved.getId())).thenReturn(detached);
        final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ProductController(manager, mock(CategoryManager.class), mock(ImageConversionService.class)))
                .build();
        mvc.perform(get("/products/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(category.getId()))
                .andExpect(jsonPath("$.categoryLabel").value("Porcelain artwork"))
                .andExpect(jsonPath("$.packageId").value(packaging.getId()))
                .andExpect(jsonPath("$.packageLabel").value("Gift box"))
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.tags[0]").value("Hand painted"))
                .andExpect(jsonPath("$.images").isArray());
    }
}
