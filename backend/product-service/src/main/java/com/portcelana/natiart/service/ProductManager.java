package com.portcelana.natiart.service;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.storage.InputFile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

public interface ProductManager {
    Optional<Product> getProduct(String id);

    Optional<Product> getProductWithImages(String id);

    Product getProductOrDie(String id);

    Product getProductWithImagesOrDie(String id);

    List<Product> getProducts(Pageable pageable);

    List<Product> getNewProducts(Pageable pageable);

    List<Product> getFeaturedProducts(Pageable pageable);

    List<Product> getProductsByCategory(Category category, Pageable pageable);

    boolean existsByCategory(Category category);

    Product createProduct(ProductDto productDto, List<InputFile> imagesInput);

    Product updateProduct(ProductDto productDto, List<InputFile> imagesInput);

    Product decreaseProductStockQuantityBy(String productId, int quantityToDecrease);

    void deleteProduct(String id);

    InputStreamResource getProductImage(String path) throws URISyntaxException, IOException;

    Product inverseVisibility(String productId);
}
