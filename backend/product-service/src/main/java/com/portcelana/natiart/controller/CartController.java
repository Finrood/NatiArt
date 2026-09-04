package com.portcelana.natiart.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.portcelana.natiart.dto.CartItemDto;
import com.portcelana.natiart.helper.TargetUser;
import com.portcelana.natiart.service.CartManager;

@RestController
public class CartController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CartController.class);

    private final CartManager cartManager;

    public CartController(CartManager cartManager) {
        this.cartManager = cartManager;
    }

    @GetMapping("/cart")
    @PreAuthorize("isFullyAuthenticated()")
    public List<CartItemDto> getCart(@TargetUser String username) {
        LOGGER.info("Getting cart of user [{}]", username);

        return cartManager.getCartItemsByUsername(username);
    }

    @DeleteMapping("/cart/clear")
    @PreAuthorize("isFullyAuthenticated()")
    public void clearCart(@TargetUser String username) {
        LOGGER.info("Clearing cart of user [{}]", username);

        cartManager.clearCart(username);
    }

    @PostMapping("/cart/item/{productId}/add")
    @PreAuthorize("isFullyAuthenticated()")
    public CartItemDto addProductToCart(@TargetUser String username, @PathVariable String productId) {
        LOGGER.info("Adding product with id [{}] to cart of user [{}]", productId, username);
        return cartManager.createCartItem(username, productId);
    }

    @DeleteMapping("/cart/item/{productId}/delete")
    @PreAuthorize("isFullyAuthenticated()")
    public void deleteCartItem(@TargetUser String username, @PathVariable String productId) {
        LOGGER.info("Deleting product with id [{}] in cart of user [{}]", productId, username);

        cartManager.deleteCartItem(username, productId);
    }
}
