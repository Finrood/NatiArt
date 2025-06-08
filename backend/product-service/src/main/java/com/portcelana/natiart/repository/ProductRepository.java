package com.portcelana.natiart.repository;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    Page<Product> findAllByCategory(Category category, Pageable pageable);

    Page<Product> findAllByNewProduct(boolean newProduct, Pageable pageable);

    Page<Product> findAllByFeaturedProduct(boolean featuredProduct, Pageable pageable);

    boolean existsByCategory(Category category);

}
