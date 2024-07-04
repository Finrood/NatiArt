package com.portcelana.natiart.service;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductManager {
    Optional<Product> getProduct(String id);
    Product getProductOrDie(String id);
    List<Product> getProducts();
    List<Product>getProductsByCategory(Category category);
    Product createProduct(ProductDto productDto);
    Product updateProduct(ProductDto productDto);
    Product hideProduct(String id);
    void deleteProduct(String id);
}
