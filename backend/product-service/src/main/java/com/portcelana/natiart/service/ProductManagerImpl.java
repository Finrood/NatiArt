package com.portcelana.natiart.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.CartItemRepository;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.storage.InputFile;
import com.portcelana.natiart.storage.StorageService;

@Service
public class ProductManagerImpl implements ProductManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductManagerImpl.class);
    private static final BigDecimal MAX_PRODUCT_WEIGHT_KG = BigDecimal.valueOf(1000);
    private static final String IMAGE_KEY_PREFIX = "products/";
    private static final int MAX_IMAGES_PER_PRODUCT = 10;

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final CategoryManager categoryManager;
    private final PackageManager packageManager;
    private final StorageService storageService;
    private final ProductImageLifecycle imageLifecycle;

    public ProductManagerImpl(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            CartItemRepository cartItemRepository,
            CategoryManager categoryManager,
            PackageManager packageManager,
            StorageService storageService,
            ProductImageLifecycle imageLifecycle) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.categoryManager = categoryManager;
        this.packageManager = packageManager;
        this.storageService = storageService;
        this.imageLifecycle = imageLifecycle;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProduct(String id) {
        if (id == null) {
            // findById(null) throws InvalidDataAccessApiUsageException (500
            // via the catch-all advice); an unknown id is a 404 instead.
            // Same precedent as PackageManagerImpl.getPackage.
            return Optional.empty();
        }
        return productRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProductWithImages(String id) {
        if (id == null) {
            return Optional.empty();
        }
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
    public Product getActiveProductWithImagesOrDie(String id) {
        return productRepository
                .findActiveByIdWithImages(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id [" + id + "] not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Product> getProductsOrDie(Collection<String> ids) {
        final Map<String, Product> byId = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (String id : ids) {
            if (!byId.containsKey(id)) {
                throw new ResourceNotFoundException("Product with id [" + id + "] not found");
            }
        }
        return byId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getProducts(Pageable pageable) {
        return fetchPageWithImages(productRepository.findAllIds(pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getActiveProducts(Pageable pageable) {
        return fetchPageWithImages(productRepository.findAllActiveIds(pageable), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getNewProducts(Pageable pageable) {
        return fetchPageWithImages(productRepository.findAllIdsByNewProduct(true, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getActiveNewProducts(Pageable pageable) {
        return fetchPageWithImages(productRepository.findAllActiveIdsByNewProduct(true, pageable), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getFeaturedProducts(Pageable pageable) {
        return fetchPageWithImages(productRepository.findAllIdsByFeaturedProduct(true, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getActiveFeaturedProducts(Pageable pageable) {
        return fetchPageWithImages(productRepository.findAllActiveIdsByFeaturedProduct(true, pageable), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(Category category, Pageable pageable) {
        return fetchPageWithImages(productRepository.findAllIdsByCategory(category, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getActiveProductsByCategory(Category category, Pageable pageable) {
        return fetchPageWithImages(productRepository.findAllActiveIdsByCategory(category, pageable), true);
    }

    private List<Product> fetchPageWithImages(Page<String> idPage) {
        return fetchPageWithImages(idPage, false);
    }

    private List<Product> fetchPageWithImages(Page<String> idPage, boolean activeOnly) {
        final List<String> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return List.of();
        }
        final List<Product> fetched = activeOnly
                ? productRepository.findAllActiveWithImagesByIds(ids)
                : productRepository.findAllWithImagesByIds(ids);
        final Map<String, Product> byId =
                fetched.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
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
        final String label = requireNonBlankLabel(productDto.getLabel());
        requireNonNullPrice(productDto.getOriginalPrice());
        requireNonNegativePrice(productDto.getOriginalPrice(), "original");
        requireNonNegativePrice(productDto.getMarkedPrice(), "marked");
        requireNonNegativeStock(productDto.getStockQuantity());
        requirePositiveWeight(productDto.getWeightKg());
        final Category category = categoryManager.getCategoryOrDie(productDto.getCategoryId());
        final Optional<Package> pack = packageManager.getPackage(productDto.getPackageId());
        final Product product = productRepository
                .save(new Product(label, productDto.getOriginalPrice())
                        .setDescription(productDto.getDescription())
                        .setCategory(category)
                        .setPackaging(pack.orElse(null))
                        .setHasFixedGoldenBorder(productDto.getHasFixedGoldenBorder())
                        .setAvailablePersonalizations(productDto.getAvailablePersonalizations())
                        .setMarkedPrice(productDto.getMarkedPrice())
                        .setStockQuantity(productDto.getStockQuantity())
                        .setWeightKg(productDto.getWeightKg())
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
        final String label = requireNonBlankLabel(productDto.getLabel());
        requireNonNullPrice(productDto.getOriginalPrice());
        requireNonNegativePrice(productDto.getOriginalPrice(), "original");
        requireNonNegativePrice(productDto.getMarkedPrice(), "marked");
        requireNonNegativeStock(productDto.getStockQuantity());
        requirePositiveWeight(productDto.getWeightKg());
        final Category category = categoryManager.getCategoryOrDie(productDto.getCategoryId());
        final Optional<Package> pack = packageManager.getPackage(productDto.getPackageId());
        final Product product = getProductOrDie(productDto.getId());
        product.setLabel(label)
                .setDescription(productDto.getDescription())
                .setCategory(category)
                .setPackaging(pack.orElse(null))
                .setHasFixedGoldenBorder(productDto.getHasFixedGoldenBorder())
                .setAvailablePersonalizations(productDto.getAvailablePersonalizations())
                .setOriginalPrice(productDto.getOriginalPrice())
                .setMarkedPrice(productDto.getMarkedPrice())
                .setStockQuantity(productDto.getStockQuantity())
                .setWeightKg(productDto.getWeightKg())
                .setTags(productDto.getTags())
                .setNewProduct(productDto.isNewProduct())
                .setFeaturedProduct(productDto.isFeaturedProduct());

        final List<String> imagesUris = processImages(product, productDto.getImages(), imagesInput);
        product.setImages(imagesUris);
        final Product saved = productRepository.save(product);

        return saved;
    }

    @Override
    @Transactional
    public void deleteProduct(String id) {
        if (id == null) {
            // deleteById(null) throws InvalidDataAccessApiUsageException
            // (500 via the catch-all advice); an unknown id is a 404 instead.
            throw new ResourceNotFoundException("Product with id [null] not found");
        }
        final Product product = getProductOrDie(id);
        if (orderRepository.existsByProduct(product) || cartItemRepository.existsByProduct(product)) {
            throw new IllegalArgumentException(
                    "Product [" + product.getLabel() + "] is referenced by an order or cart; deactivate it instead");
        }
        imageLifecycle.prepareReferences(product.getImages(), List.of());
        productRepository.delete(product);
    }

    @Override
    public InputStreamResource getProductImage(String path) {
        final URI uri;
        try {
            uri = new URI(path);
        } catch (URISyntaxException e) {
            // Malformed paths are a client error (400 via ControllerAdvice),
            // not a server failure.
            throw new IllegalArgumentException("Invalid image path: " + path);
        }
        final InputStream inputStream = storageService.openFile(uri);
        return new InputStreamResource(inputStream);
    }

    @Override
    @Transactional
    public Product inverseVisibility(String productId) {
        // The bulk flip increments @Version and clears managed state before the
        // complete reload, so a stale product save cannot undo this change.
        if (productRepository.toggleActiveById(productId) == 0) {
            throw new ResourceNotFoundException("Product with id [" + productId + "] not found");
        }
        return productRepository
                .findByIdWithImages(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id [" + productId + "] not found"));
    }

    private List<String> processImages(Product product, List<String> existingImages, List<InputFile> newImages) {
        final List<InputFile> uploads = newImages != null ? newImages : List.of();
        try {
            final List<String> retainedImages = existingImages != null ? existingImages : List.of();
            if (retainedImages.size() + uploads.size() > MAX_IMAGES_PER_PRODUCT) {
                throw new IllegalArgumentException(
                        "A product may contain at most " + MAX_IMAGES_PER_PRODUCT + " images");
            }
            LOGGER.info(
                    "Processing [{}] images for product labelled [{}] with id [{}]",
                    uploads.size(),
                    product.getLabel(),
                    product.getId());

            imageLifecycle.prepareReferences(product.getImages(), retainedImages);
            final List<String> imagesUris = new ArrayList<>(retainedImages);

            for (InputFile inputFile : uploads) {
                final String imagePath = IMAGE_KEY_PREFIX + product.getId() + "/" + UUID.randomUUID();
                imagesUris.add(imageLifecycle
                        .upload(product.getId(), imagePath, UUID.randomUUID().toString(), inputFile)
                        .toString());
            }
            return imagesUris;
        } finally {
            for (InputFile input : uploads) {
                try {
                    input.inputStream().close();
                } catch (java.io.IOException error) {
                    LOGGER.warn("Unable to close product upload input");
                }
            }
        }
    }

    private static String requireNonBlankLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Product label must not be blank");
        }
        return label.trim();
    }

    private static void requireNonNullPrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("Product price must not be null");
        }
    }

    private static void requireNonNegativePrice(BigDecimal price, String field) {
        if (price != null && price.signum() < 0) {
            throw new IllegalArgumentException("Product " + field + " price must not be negative");
        }
    }

    private static void requireNonNegativeStock(int stockQuantity) {
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("Product stock quantity must not be negative");
        }
    }

    private static void requirePositiveWeight(BigDecimal weightKg) {
        if (weightKg == null || weightKg.signum() <= 0) {
            throw new IllegalArgumentException("Product weight must be greater than zero kilograms");
        }
        if (weightKg.scale() > 3) {
            throw new IllegalArgumentException("Product weight must have at most three decimal places");
        }
        if (weightKg.compareTo(MAX_PRODUCT_WEIGHT_KG) > 0) {
            throw new IllegalArgumentException("Product weight must not exceed 1000 kilograms");
        }
    }
}
