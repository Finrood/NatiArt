package com.portcelana.natiart.repository;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class ProductRepositoryPaginationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private com.portcelana.natiart.repository.CategoryRepository categoryRepository;

    private Category category;

    @BeforeEach
    void seedCategory() {
        category = categoryRepository.save(new Category("cat-" + System.nanoTime()));
    }

    private Product newProduct(String label, boolean featured) {
        final Product product = new Product(label, new BigDecimal("10.00"));
        product.setFeaturedProduct(featured);
        product.setCategory(category);
        return product;
    }

    @Test
    void pagedIdQueriesSliceWithoutCollectionFetch() {
        Product first = null;
        Product second = null;
        for (int i = 0; i < 5; i++) {
            Product p = newProduct("product-" + i, i % 2 == 0);
            if (i % 2 == 0) {
                p.getImages().add("file:///tmp/product-images/" + i + "/a.webp");
            }
            p = productRepository.save(p);
            if (i == 0) first = p;
            if (i == 1) second = p;
        }

        PageRequest firstPage = PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "label"));
        Page<String> page = productRepository.findAllIds(firstPage);

        assertEquals(5, page.getTotalElements());
        assertEquals(List.of(first.getId(), second.getId()), page.getContent());

        Page<String> featuredPage = productRepository.findAllIdsByFeaturedProduct(true,
                PageRequest.of(0, 2, Sort.by("label")));
        assertEquals(3, featuredPage.getTotalElements());
    }

    @Test
    void fetchByIdsLoadsImagesEagerly() {
        Product b = productRepository.save(newProduct("b-product", true));
        Product c = productRepository.save(newProduct("c-product", true));
        b.getImages().add("file:///img/b1.webp");
        c.getImages().add("file:///img/c1.webp");
        productRepository.save(b);
        productRepository.save(c);

        List<Product> fetched = productRepository.findAllWithImagesByIds(
                List.of(c.getId(), b.getId()));

        assertEquals(2, fetched.size());
        for (Product product : fetched) {
            assertTrue(Hibernate.isInitialized(product.getImages()));
            assertFalse(product.getImages().isEmpty());
        }
    }

    @Test
    void emptyIdListReturnsNoRowsAndEmptyPageHasNoContent() {
        Page<String> beyondEnd = productRepository.findAllIds(PageRequest.of(9, 20));
        assertTrue(beyondEnd.getContent().isEmpty());
        assertTrue(productRepository.findAllWithImagesByIds(List.of()).isEmpty());
    }
}
