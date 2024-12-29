package com.portcelana.natiart.controller;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.helper.TargetUser;
import com.portcelana.natiart.model.CartItem;
import com.portcelana.natiart.service.CartManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CartController {
    public static Logger LOGGER = LoggerFactory.getLogger(CartController.class);

    final private CartManager cartManager;

    public CartController(CartManager cartManager) {
        this.cartManager = cartManager;
    }

    @GetMapping("/cart")
    public List<ProductDto> getCart(@TargetUser String username) {
        LOGGER.info("Getting cart of user [{}]", username);

        return cartManager.getCartItemsByUsername(username);
    }

    @DeleteMapping("/cart/clear")
    public void clearCart(@TargetUser String username) {
        LOGGER.info("Clearing cart of user [{}]", username);

        cartManager.clearCart(username);
    }

    @PostMapping("/cart/item/{productId}/add")
    public CartItem addProductToCart(@TargetUser String username, @PathVariable String productId) {
        LOGGER.info("Adding product with id [{}] to cart of user [{}]", productId, username);
        return cartManager.createCartItem(username, productId);
    }

    @DeleteMapping("/cart/item/{productId}/delete")
    public void deleteCartItem(@TargetUser String username, @PathVariable String productId) {
        LOGGER.info("Deleting product with id [{}] in cart of user [{}]", productId, username);

        cartManager.deleteCartItem(username, productId);
    }
}
