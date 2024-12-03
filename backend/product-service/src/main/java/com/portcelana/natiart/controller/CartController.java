package com.portcelana.natiart.controller;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.helper.TargetUser;
import com.portcelana.natiart.model.CartItem;
import com.portcelana.natiart.service.CartManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CartController {
    final private CartManager cartManager;

    public CartController(CartManager cartManager) {
        this.cartManager = cartManager;
    }

    @GetMapping("/cart")
    public List<ProductDto> getCart(@TargetUser String username) {
        return cartManager.getCartItemsByUsername(username);
    }

    @DeleteMapping("/cart/clear")
    public void clearCart(@TargetUser String username) {
        cartManager.clearCart(username);
    }

    @PostMapping("/cart/item/add")
    public CartItem addProductToCart(@TargetUser String username, String productId) {
        return cartManager.createCartItem(username, productId);
    }

    @DeleteMapping("/cart/item/delete/{productId}")
    public void clearCart(@TargetUser String username, @PathVariable String productId) {
        cartManager.deleteCartItem(username, productId);
    }
}
