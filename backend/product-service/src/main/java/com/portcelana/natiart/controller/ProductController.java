package com.portcelana.natiart.controller;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.helper.TargetUser;
import com.portcelana.natiart.service.ImageConversionService;
import com.portcelana.natiart.service.ProductManager;
import com.portcelana.natiart.storage.InputFile;

@RestController
public class ProductController {
    public static Logger LOGGER = LoggerFactory.getLogger(ProductController.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductManager productManager;
    private final ImageConversionService imageConversionService;

    public ProductController(ProductManager productManager, ImageConversionService imageConversionService) {
        this.productManager = productManager;
        this.imageConversionService = imageConversionService;
    }

    @GetMapping("/products/{productId}")
    public ProductDto getProduct(@PathVariable String productId) {
        LOGGER.info("Getting product with id [{}]", productId);

        return ProductDto.from(productManager.getProductWithImagesOrDie(productId));
    }

    @GetMapping("/products")
    public List<ProductDto> getProducts(
            @TargetUser String username,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        LOGGER.info("Getting all products");
        Pageable pageable = toPageable(page, size);
        return productManager.getProducts(pageable).stream()
                .map(ProductDto::from)
                .toList();
    }

    @GetMapping("/products/new")
    public List<ProductDto> getNewProducts(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        LOGGER.info("Getting new products");
        Pageable pageable = toPageable(page, size);
        return productManager.getNewProducts(pageable).stream()
                .map(ProductDto::from)
                .toList();
    }

    @GetMapping("/products/featured")
    public List<ProductDto> getFeaturedProducts(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        LOGGER.info("Getting featured products");
        Pageable pageable = toPageable(page, size);
        return productManager.getFeaturedProducts(pageable).stream()
                .map(ProductDto::from)
                .toList();
    }

    @PostMapping(value = "/products/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductDto createProduct(
            @RequestPart("productDto") ProductDto productDto,
            @RequestPart(value = "newImages", required = false) List<MultipartFile> newImages)
            throws IOException {
        LOGGER.info(
                "Creating new product with label [{}] description [{}]",
                productDto.getLabel(),
                productDto.getDescription());

        final List<InputFile> imagesInput = processImages(newImages);

        return ProductDto.from(productManager.createProduct(productDto, imagesInput));
    }

    @PutMapping("/products/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductDto updateProduct(
            @PathVariable String productId,
            @RequestPart("productDto") ProductDto productDto,
            @RequestPart(value = "newImages", required = false) List<MultipartFile> newImages)
            throws IOException {
        Assert.isTrue(productId.equals(productDto.getId()), "product ids are not equal!");

        final List<InputFile> imagesInput = processImages(newImages);

        return ProductDto.from(productManager.updateProduct(productDto, imagesInput));
    }

    @PatchMapping("/products/{productId}/visibility/inverse")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductDto inverseProductVisibility(@PathVariable String productId) {
        return ProductDto.from(productManager.inverseVisibility(productId));
    }

    @DeleteMapping("/products/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteProduct(@PathVariable String productId) {
        productManager.deleteProduct(productId);
    }

    @GetMapping("/images")
    public ResponseEntity<Resource> getProductImage(@RequestParam String path) throws URISyntaxException, IOException {
        LOGGER.info("Getting image with path [{}]", path);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"product-image.webp\"")
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(productManager.getProductImage(path));
    }

    private static Pageable toPageable(int page, int size) {
        final int safePage = Math.max(0, page);
        final int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "label"));
    }

    private List<InputFile> processImages(List<MultipartFile> images) throws IOException {
        if (images == null) {
            return new ArrayList<>();
        }
        final List<MultipartFile> convertedImages = imageConversionService.convertToWebP(images);
        return convertedImages.stream()
                .map(image -> {
                    try {
                        return InputFile.from(image);
                    } catch (IOException e) {
                        throw new RuntimeException("Error processing image", e);
                    }
                })
                .toList();
    }
}
