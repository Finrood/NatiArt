package com.portcelana.natiart.service;

import com.portcelana.natiart.controller.ProductController;
import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.storage.InputFile;
import com.portcelana.natiart.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductManagerImpl implements ProductManager {
    public static Logger LOGGER = LoggerFactory.getLogger(ProductController.class);
    private static final String IMAGE_BASE_PATH = "product-images/";

    private final ProductRepository productRepository;
    private final CategoryManager categoryManager;
    private final PackageManager packageManager;
    private final StorageService storageService;

    public ProductManagerImpl(ProductRepository productRepository,
                              CategoryManager categoryManager,
                              PackageManager packageManager,
                              StorageService storageService) {
        this.productRepository = productRepository;
        this.categoryManager = categoryManager;
        this.packageManager = packageManager;
        this.storageService = storageService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProduct(String id) {
        return productRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProductWithImages(String id) {
        return productRepository.findByIdWithImages(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Product getProductOrDie(String id) {
        return getProduct(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id [" + id + "] not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Product getProductWithImagesOrDie(String id) {
        return getProductWithImages(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id [" + id + "] not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getProducts(Pageable pageable) {
        return fetchPageWithImages(productRepository.findAllIds(pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getNewProducts(Pageable pageable) {
        return fetchPageWithImages(productRepository.findAllIdsByNewProduct(true, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getFeaturedProducts(Pageable pageable) {
        return fetchPageWithImages(productRepository.findAllIdsByFeaturedProduct(true, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(Category category, Pageable pageable) {
        return fetchPageWithImages(productRepository.findAllIdsByCategory(category, pageable));
    }

    private List<Product> fetchPageWithImages(Page<String> idPage) {
        final List<String> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return List.of();
        }
        final Map<String, Product> byId = productRepository.findAllWithImagesByIds(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return ids.stream()
                .map(byId::get)
                // A product deleted between the id-page query and the fetch query simply drops from the page
                // instead of surfacing a NullPointerException to the storefront.
                .filter(Objects::nonNull)
                .toList();
    }
    @Override
    @Transactional(readOnly = true)
    public boolean existsByCategory(Category category) {
        return productRepository.existsByCategory(category);
    }

    @Override
    @Transactional
    public Product createProduct(ProductDto productDto, List<InputFile> imagesInput) {
        final Category category = categoryManager.getCategoryOrDie(productDto.getCategoryId());
        final Optional<Package> pack = packageManager.getPackage(productDto.getPackageId());
        final Product product = productRepository.save(new Product(productDto.getLabel(), productDto.getOriginalPrice())
                        .setDescription(productDto.getDescription())
                        .setCategory(category)
                        .setPackaging(pack.orElse(null))
                        .setHasFixedGoldenBorder(productDto.getHasFixedGoldenBorder())
                        .setAvailablePersonalizations(productDto.getAvailablePersonalizations())
                        .setMarkedPrice(productDto.getMarkedPrice())
                        .setStockQuantity(productDto.getStockQuantity())
                        .setTags(productDto.getTags()))
                .setNewProduct(productDto.isNewProduct())
                .setFeaturedProduct(productDto.isFeaturedProduct());

        final List<String> imagesUris = processImages(product, productDto.getImages(), imagesInput);
        product.setImages(imagesUris);

        return productRepository.save(product);
    }

    @Override
    @Transactional
    public Product updateProduct(ProductDto productDto, List<InputFile> imagesInput) {
        final Category category = categoryManager.getCategoryOrDie(productDto.getCategoryId());
        final Optional<Package> pack = packageManager.getPackage(productDto.getPackageId());
        final Product product = getProductOrDie(productDto.getId())
                .setLabel(productDto.getLabel())
                .setDescription(productDto.getDescription())
                .setCategory(category)
                .setPackaging(pack.orElse(null))
                .setHasFixedGoldenBorder(productDto.getHasFixedGoldenBorder())
                .setAvailablePersonalizations(productDto.getAvailablePersonalizations())
                .setOriginalPrice(productDto.getOriginalPrice())
                .setMarkedPrice(productDto.getMarkedPrice())
                .setStockQuantity(productDto.getStockQuantity())
                .setTags(productDto.getTags())
                .setNewProduct(productDto.isNewProduct())
                .setFeaturedProduct(productDto.isFeaturedProduct());

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
    public InputStreamResource getProductImage(String path) throws URISyntaxException {
        final URI uri = new URI(path);
        final InputStream inputStream = storageService.openFile(uri);
        return new InputStreamResource(inputStream);
    }

    @Override
    @Transactional
    public Product inverseVisibility(String productId) {
        final Product product = getProductOrDie(productId);
        product.setActive(!product.isActive());
        return productRepository.save(product);
    }

    private List<String> processImages(Product product, List<String> existingImages, List<InputFile> newImages) {
        LOGGER.info("Processing [{}] images for product labelled [{}] with id [{}]", newImages.size(), product.getLabel(), product.getId());

        final List<String> imagesUris = existingImages != null ? new ArrayList<>(existingImages) : new ArrayList<>();

        List<String> newUris = newImages.parallelStream()
                .map(inputFile -> {
                    final String imagePath = IMAGE_BASE_PATH + product.getId() + "/" + UUID.randomUUID();
                    final URI imageUri = storageService.uploadFile(imagePath, inputFile, UUID.randomUUID().toString());
                    return imageUri.toString();
                })
                .toList();

        imagesUris.addAll(newUris);
        return imagesUris;
    }
}