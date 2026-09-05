package com.portcelana.natiart.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.CartItemDto;
import com.portcelana.natiart.model.CartItem;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.CartItemRepository;

@Service
public class CartManagerImpl implements CartManager {
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
        // Atomic increment first: concurrent adds serialize in the database instead
        // of losing increments in a read-modify-write round trip. The unique
        // constraint on (username, product) keeps a lost insert race fail-loud
        // instead of persisting duplicate lines.
        if (cartItemRepository.incrementQuantity(username, productId) > 0) {
            return CartItemDto.from(getCartLineOrDie(username, product));
        }
        final CartItem cartItem = cartItemRepository.save(new CartItem(username, product));
        return CartItemDto.from(cartItem);
    }

    @Override
    @Transactional
    public void decreaseCartItemQuantity(String username, String productId) {
        final Optional<Product> product = productManager.getProduct(productId);
        if (product.isEmpty()) {
            return;
        }
        // Atomic guarded decrement: only the last remaining unit falls through to
        // the idempotent delete, so concurrent decreases cannot lose updates.
        if (cartItemRepository.decrementQuantityIfGreaterThanOne(username, productId) == 0) {
            cartItemRepository.deleteByUsernameAndProduct(username, product.get());
        }
    }

    @Override
    @Transactional
    public void deleteCartItem(String username, String productId) {
        productManager
                .getProduct(productId)
                .flatMap(product -> cartItemRepository.findCartItemByUsernameAndProduct(username, product))
                .ifPresent(cartItemRepository::delete);
    }

    @Override
    @Transactional
    public void clearCart(String username) {
        cartItemRepository.deleteAll(cartItemRepository.findCartItemsByUsername(username));
    }

    private CartItem getCartLineOrDie(String username, Product product) {
        return cartItemRepository
                .findCartItemByUsernameAndProduct(username, product)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cart item for user [" + username + "] and product [" + product.getId() + "] not found"));
    }
}
