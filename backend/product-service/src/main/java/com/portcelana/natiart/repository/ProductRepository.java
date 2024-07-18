package com.portcelana.natiart.repository;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, String>  {
    List<Product> findAllByCategory(Category category);
    List<Product> findAllByNewProduct(boolean newProduct);
    List<Product> findAllByFeaturedProduct(boolean featuredProduct);

}
