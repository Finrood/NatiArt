package com.portcelana.natiart.controller;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.helper.TargetUser;
import com.portcelana.natiart.service.ImageConversionService;
import com.portcelana.natiart.service.ProductManager;
import com.portcelana.natiart.storage.InputFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@RestController
public class ProductController {
    public static Logger LOGGER = LoggerFactory.getLogger(ProductController.class);

    final private ProductManager productManager;
    private final ImageConversionService imageConversionService;

    public ProductController(ProductManager productManager,
                             ImageConversionService imageConversionService) {
        this.productManager = productManager;
        this.imageConversionService = imageConversionService;
    }

    @GetMapping("/products/{productId}")
    public ProductDto getProduct(@PathVariable String productId) {
        LOGGER.info("Getting product with id [{}]", productId);

        return ProductDto.from(productManager.getProductWithImagesOrDie(productId));
    }

    @GetMapping("/products")
    public List<ProductDto> getProducts(@TargetUser String username,
                                        @RequestParam(required = false, defaultValue = "0") int page,
                                        @RequestParam(required = false, defaultValue = "20") int size) {
        LOGGER.info("Getting all products");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "label"));
        return productManager.getProducts(pageable).stream()
                .map(ProductDto::from)
                .toList();
    }

    @GetMapping("/products/new")
    public List<ProductDto> getNewProducts(@RequestParam(required = false, defaultValue = "0") int page,
                                           @RequestParam(required = false, defaultValue = "20") int size) {
        LOGGER.info("Getting new products");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "label"));
        return productManager.getNewProducts(pageable).stream()
                .map(ProductDto::from)
                .toList();
    }

    @GetMapping("/products/featured")
    public List<ProductDto> getFeaturedProducts(@RequestParam(required = false, defaultValue = "0") int page,
                                                @RequestParam(required = false, defaultValue = "20") int size) {
        LOGGER.info("Getting featured products");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "label"));
        return productManager.getFeaturedProducts(pageable).stream()
                .map(ProductDto::from)
                .toList();
    }

    @PostMapping(value = "/products/create")
    public ProductDto createProduct(@RequestPart("productDto") ProductDto productDto,
                                    @RequestPart(value = "newImages", required = false) List<MultipartFile> newImages) throws IOException {
        LOGGER.info("Creating new product with label [{}] description [{}]",
                productDto.getLabel(),
                productDto.getDescription());

        final List<InputFile> imagesInput = processImages(newImages);

        return ProductDto.from(productManager.createProduct(productDto, imagesInput));
    }

    @PutMapping("/products/{productId}")
    public ProductDto updateProduct(@PathVariable String productId,
                                    @RequestPart("productDto") ProductDto productDto,
                                    @RequestPart(value = "newImages", required = false) List<MultipartFile> newImages) throws IOException {
        Assert.isTrue(productId.equals(productDto.getId()), "product ids are not equal!");

        final List<InputFile> imagesInput = processImages(newImages);

        return ProductDto.from(productManager.updateProduct(productDto, imagesInput));
    }

    @PatchMapping("/products/{productId}/visibility/inverse")
    public ProductDto inverseProductVisibility(@PathVariable String productId) {
        return ProductDto.from(productManager.inverseVisibility(productId));
    }

    @DeleteMapping("/products/{productId}")
    public void deleteProduct(@PathVariable String productId) {
        productManager.deleteProduct(productId);
    }

    @GetMapping("images")
    public ResponseEntity<Resource> getProductImage(@RequestParam String path) throws URISyntaxException, IOException {
        LOGGER.info("Getting image with path [{}]", path);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"product-image.webp\"")
                .body(productManager.getProductImage(path));
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
