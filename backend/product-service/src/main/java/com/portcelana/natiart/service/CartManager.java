package com.portcelana.natiart.service;

import java.util.List;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.model.CartItem;

public interface CartManager {
    List<ProductDto> getCartItemsByUsername(String username);

    CartItem createCartItem(String username, String productId);

    void decreaseCartItemQuantity(String username, String productId);

    void deleteCartItem(String username, String productId);

    void clearCart(String username);
}
