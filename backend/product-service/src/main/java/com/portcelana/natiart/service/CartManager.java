package com.portcelana.natiart.service;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.model.CartItem;

import java.util.List;

public interface CartManager {
    List<ProductDto> getCartItemsByUsername(String username);
    CartItem createCartItem(String username, String productId);
    void decreaseCartItemQuantity(String username, String productId);
    void deleteCartItem(String username, String productId);
    void clearCart(String username);
}
