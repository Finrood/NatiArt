package com.portcelana.natiart.controller;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.helper.TargetUser;
import com.portcelana.natiart.service.ProductManager;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
public class ProductController {
    final private ProductManager productManager;

    public ProductController(ProductManager productManager) {
        this.productManager = productManager;
    }

    @GetMapping("/products/{productId}")
    public ProductDto getProduct(@PathVariable String productId) {
        return ProductDto.from(productManager.getProductOrDie(productId));
    }

    @GetMapping("/products")
    public List<ProductDto> getProducts(@TargetUser String username) {
        return productManager.getProducts().stream()
                .map(ProductDto::from)
                .sorted(Comparator.comparing(ProductDto::getLabel))
                .toList();
    }

    @PostMapping("/products/create")
    public ProductDto createProduct(@RequestBody ProductDto productDto) {
        return ProductDto.from(productManager.createProduct(productDto));
    }

    @PutMapping("/products/{productId}")
    public ProductDto updateProduct(@PathVariable String productId, @RequestBody ProductDto productDto) {
        Assert.isTrue(productId.equals(productDto.getId()), "product ids are not equals !");

        return ProductDto.from(productManager.updateProduct(productDto));
    }

    @DeleteMapping("/products/{productId}/hide")
    public void hideProduct(@PathVariable String productId) {
        productManager.hideProduct(productId);
    }

    @DeleteMapping("/products/{productId}")
    public void deleteProduct(@PathVariable String productId) {
        productManager.deleteProduct(productId);
    }
}
