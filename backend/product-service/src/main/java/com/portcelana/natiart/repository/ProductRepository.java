package com.portcelana.natiart.repository;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.images WHERE p.id = :id")
    Optional<Product> findByIdWithImages(String id);

    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity WHERE p.id = :id AND p.stockQuantity >= :quantity")
    int decreaseStockIfAvailable(@Param("id") String id, @Param("quantity") int quantity);

    @Query(value = "SELECT p FROM Product p LEFT JOIN FETCH p.images", countQuery = "SELECT count(p) FROM Product p")
    Page<Product> findAllWithImages(Pageable pageable);

    Page<Product> findAllByCategory(Category category, Pageable pageable);

    @Query(value = "SELECT p FROM Product p LEFT JOIN FETCH p.images WHERE p.newProduct = :newProduct", countQuery = "SELECT count(p) FROM Product p WHERE p.newProduct = :newProduct")
    Page<Product> findAllByNewProduct(boolean newProduct, Pageable pageable);

    @Query(value = "SELECT p FROM Product p LEFT JOIN FETCH p.images WHERE p.featuredProduct = :featuredProduct", countQuery = "SELECT count(p) FROM Product p WHERE p.featuredProduct = :featuredProduct")
    Page<Product> findAllByFeaturedProduct(boolean featuredProduct, Pageable pageable);

    boolean existsByCategory(Category category);

}
