package com.portcelana.natiart.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.dto.CartItemDto;
import com.portcelana.natiart.model.CartItem;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.CartItemRepository;

@Service
public class CartManagerImpl implements CartManager {
    // Mirrors the order line cap (OrderManagerImpl MAX_ITEM_QUANTITY): a cart
    // line grown past it could never be ordered, so reject the add instead.
    private static final int MAX_LINE_QUANTITY = 100;

    private final CartItemRepository cartItemRepository;
    private final ProductManager productManager;

    public CartManagerImpl(CartItemRepository cartItemRepository, ProductManager productManager) {
        this.cartItemRepository = cartItemRepository;
        this.productManager = productManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartItemDto> getCartItemsByUsername(String username) {
        return cartItemRepository.findCartItemsByUsername(username).stream()
                .map(CartItemDto::from)
                .toList();
    }

    @Override
    @Transactional
    public CartItemDto createCartItem(String username, String productId) {
        final Product product = productManager.getProductOrDie(productId);
        if (!product.isActive()) {
            throw new IllegalArgumentException("Product [" + product.getLabel() + "] is no longer available");
        }
        // Add and remove must decide under the same row lock. A failed first
        // insert still fails on the database's unique user/product key.
        final CartItem existing = cartItemRepository
                .findCartItemByUsernameAndProductForUpdate(username, productId)
                .orElse(null);
        if (existing != null) {
            if (existing.getQuantity() >= MAX_LINE_QUANTITY) {
                throw new IllegalArgumentException(
                        "Cart line for product [" + product.getLabel() + "] must not exceed " + MAX_LINE_QUANTITY);
            }
            existing.increaseQuantity();
            final CartItem detailed = cartItemRepository
                    .findCartItemByUsernameAndProductWithDetails(username, productId)
                    .orElseThrow(() -> new IllegalStateException("Locked cart line disappeared"));
            return CartItemDto.from(detailed);
        }
        final CartItem cartItem = cartItemRepository.save(new CartItem(username, product));
        return CartItemDto.from(cartItem);
    }

    @Override
    @Transactional
    public void decreaseCartItemQuantity(String username, String productId) {
        if (productManager.getProduct(productId).isEmpty()) {
            return;
        }
        cartItemRepository
                .findCartItemByUsernameAndProductForUpdate(username, productId)
                .ifPresent(cartItem -> {
                    if (cartItem.getQuantity() > 1) {
                        cartItem.decreaseQuantity();
                    } else {
                        cartItemRepository.delete(cartItem);
                    }
                });
    }

    @Override
    @Transactional
    public void deleteCartItem(String username, String productId) {
        cartItemRepository
                .findCartItemByUsernameAndProductForUpdate(username, productId)
                .ifPresent(cartItemRepository::delete);
    }

    @Override
    @Transactional
    public void clearCart(String username) {
        cartItemRepository.deleteByUsername(username);
    }
}
