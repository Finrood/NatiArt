package com.portcelana.natiart.service;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductManagerImpl implements ProductManager {
    private final ProductRepository productRepository;
    private final CategoryManager categoryManager;

    public ProductManagerImpl(ProductRepository productRepository,
                              CategoryManager categoryManager) {
        this.productRepository = productRepository;
        this.categoryManager = categoryManager;
    }

    @Override
    public Optional<Product> getProduct(String id) {
        return productRepository.findById(id);
    }

    @Override
    public Product getProductOrDie(String id) {
        return getProduct(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Product with id %s not found", id)));
    }

    @Override
    public List<Product> getProducts() {
        return productRepository.findAll();
    }

    @Override
    public List<Product> getProductsByCategory(Category category) {
        return productRepository.findAllByCategory(category);
    }

    @Override
    public Product createProduct(ProductDto productDto) {
        final Category category = categoryManager.getCategoryOrDie(productDto.getCategoryId());
        final Product product = new Product(productDto.getLabel(), productDto.getOriginalPrice())
                .setDescription(productDto.getDescription())
                .setCategory(category)
                .setMarkedPrice(productDto.getMarkedPrice())
                .setImages(productDto.getImages())
                .setStockQuantity(productDto.getStockQuantity())
                .setTags(productDto.getTags());
        return productRepository.save(product);
    }

    @Override
    public Product updateProduct(ProductDto productDto) {
        final Category category = categoryManager.getCategoryOrDie(productDto.getCategoryId());

        final Product product = getProductOrDie(productDto.getId())
                .setLabel(productDto.getLabel())
                .setDescription(productDto.getDescription())
                .setCategory(category)
                .setOriginalPrice(productDto.getOriginalPrice())
                .setMarkedPrice(productDto.getMarkedPrice())
                .setImages(productDto.getImages())
                .setStockQuantity(productDto.getStockQuantity())
                .setTags(productDto.getTags());
        return productRepository.save(product);
    }

    //TODO implements hidden/visible for model Product
    @Override
    public Product hideProduct(String id) {
        return null;
    }

    @Override
    public void deleteProduct(String id) {
        productRepository.deleteById(id);
    }
}
