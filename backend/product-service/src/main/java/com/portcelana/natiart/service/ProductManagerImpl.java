package com.portcelana.natiart.service;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.storage.InputFile;
import com.portcelana.natiart.storage.StorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProductManagerImpl implements ProductManager {
    private static final String IMAGE_BASE_PATH = "product-images/";
    private final ProductRepository productRepository;
    private final CategoryManager categoryManager;
    private final StorageService storageService;

    public ProductManagerImpl(ProductRepository productRepository,
                              CategoryManager categoryManager,
                              StorageService storageService) {
        this.productRepository = productRepository;
        this.categoryManager = categoryManager;
        this.storageService = storageService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProduct(String id) {
        return productRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Product getProductOrDie(String id) {
        return getProduct(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id [" + id + "] not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getProducts() {
        return productRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(Category category) {
        return productRepository.findAllByCategory(category);
    }

    @Override
    @Transactional
    public Product createProduct(ProductDto productDto, List<InputFile> imagesInput) {
        final Category category = categoryManager.getCategoryOrDie(productDto.getCategoryId());
        final Product product = productRepository.save(new Product(productDto.getLabel(), productDto.getOriginalPrice())
                .setDescription(productDto.getDescription())
                .setCategory(category)
                .setMarkedPrice(productDto.getMarkedPrice())
                .setStockQuantity(productDto.getStockQuantity())
                .setTags(productDto.getTags()));

        final List<String> imagesUris = processImages(product, productDto.getImages(), imagesInput);
        product.setImages(imagesUris);

        return productRepository.save(product);
    }

    @Override
    @Transactional
    public Product updateProduct(ProductDto productDto, List<InputFile> imagesInput) {
        final Category category = categoryManager.getCategoryOrDie(productDto.getCategoryId());
        final Product product = getProductOrDie(productDto.getId())
                .setLabel(productDto.getLabel())
                .setDescription(productDto.getDescription())
                .setCategory(category)
                .setOriginalPrice(productDto.getOriginalPrice())
                .setMarkedPrice(productDto.getMarkedPrice())
                .setStockQuantity(productDto.getStockQuantity())
                .setTags(productDto.getTags());

        final List<String> imagesUris = processImages(product, productDto.getImages(), imagesInput);
        product.setImages(imagesUris);

        return productRepository.save(product);
    }

    @Override
    @Transactional
    public void deleteProduct(String id) {
        productRepository.deleteById(id);
    }

    @Override
    @Transactional
    public Product inverseVisibility(String productId) {
        final Product product = getProductOrDie(productId);
        product.setActive(!product.isActive());
        return productRepository.save(product);
    }

    @Override
    public InputStreamResource getProductImage(String path) throws URISyntaxException {
        final URI uri = new URI(path);
        final InputStream inputStream = storageService.openFile(uri);
        return new InputStreamResource(inputStream);
    }

    private List<String> processImages(Product product, List<String> existingImages, List<InputFile> newImages) {
        final List<String> imagesUris = existingImages != null ? new ArrayList<>(existingImages) : new ArrayList<>();
        for (InputFile inputFile : newImages) {
            final String imagePath = IMAGE_BASE_PATH + product.getId() + "/" + UUID.randomUUID();
            final URI imageUri = storageService.uploadFile(imagePath, inputFile, UUID.randomUUID().toString());
            imagesUris.add(imageUri.toString());
        }
        return imagesUris;
    }
}