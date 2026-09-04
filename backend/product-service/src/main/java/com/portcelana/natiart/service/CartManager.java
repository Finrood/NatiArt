package com.portcelana.natiart.service;

import java.util.List;

import com.portcelana.natiart.dto.CartItemDto;

public interface CartManager {
    List<CartItemDto> getCartItemsByUsername(String username);

    CartItemDto createCartItem(String username, String productId);

    void decreaseCartItemQuantity(String username, String productId);

    void deleteCartItem(String username, String productId);

    void clearCart(String username);
}
