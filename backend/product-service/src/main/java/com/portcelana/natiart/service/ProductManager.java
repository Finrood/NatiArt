package com.portcelana.natiart.service;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.storage.InputFile;
import org.springframework.core.io.InputStreamResource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

public interface ProductManager {
    Optional<Product> getProduct(String id);
    Product getProductOrDie(String id);
    List<Product> getProducts();
    List<Product> getNewProducts();
    List<Product> getFeaturedProducts();
    List<Product>getProductsByCategory(Category category);
    Product createProduct(ProductDto productDto, List<InputFile> imagesInput);
    Product updateProduct(ProductDto productDto, List<InputFile> imagesInput);
    Product decreaseProductStockQuantityBy(String productId, int quantityToDecrease);
    void deleteProduct(String id);
    InputStreamResource getProductImage(String path) throws URISyntaxException, IOException;
    Product inverseVisibility(String productId);
}
