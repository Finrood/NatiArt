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
        final CartItem cartItem = cartItemRepository
                .findCartItemByUsernameAndProduct(username, product)
                .map(CartItem::increaseQuantity)
                .orElseGet(() -> new CartItem(username, product));
        return CartItemDto.from(cartItemRepository.save(cartItem));
    }

    @Override
    @Transactional
    public void decreaseCartItemQuantity(String username, String productId) {
        productManager
                .getProduct(productId)
                .flatMap(product -> cartItemRepository.findCartItemByUsernameAndProduct(username, product))
                .ifPresent(cartItem -> {
                    if (cartItem.getQuantity() > 1) {
                        cartItem.decreaseQuantity();
                        cartItemRepository.save(cartItem);
                    } else {
                        cartItemRepository.delete(cartItem);
                    }
                });
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
}
