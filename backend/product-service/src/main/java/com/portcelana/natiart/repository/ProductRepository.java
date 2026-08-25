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

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.images WHERE p.id = :id")
    Optional<Product> findByIdWithImages(String id);

    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity WHERE p.id = :id AND p.stockQuantity >= :quantity")
    int decreaseStockIfAvailable(@Param("id") String id, @Param("quantity") int quantity);

    @Query("SELECT p.id FROM Product p")
    Page<String> findAllIds(Pageable pageable);

    @Query("SELECT p.id FROM Product p WHERE p.newProduct = :newProduct")
    Page<String> findAllIdsByNewProduct(boolean newProduct, Pageable pageable);

    @Query("SELECT p.id FROM Product p WHERE p.featuredProduct = :featuredProduct")
    Page<String> findAllIdsByFeaturedProduct(boolean featuredProduct, Pageable pageable);

    @Query("SELECT p.id FROM Product p WHERE p.category = :category")
    Page<String> findAllIdsByCategory(Category category, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.id IN :ids")
    List<Product> findAllWithImagesByIds(@Param("ids") List<String> ids);

    boolean existsByCategory(Category category);
}
