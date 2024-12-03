package com.portcelana.natiart.service;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.model.CartItem;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.CartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public List<ProductDto> getCartItemsByUsername(String username) {
        return cartItemRepository.findCartItemsByUsername(username).stream()
                .map(CartItem::getProduct)
                .map(ProductDto::from)
                .toList();
    }

    @Override
    @Transactional
    public CartItem createCartItem(String username, String productId) {
        final Product product = productManager.getProductOrDie(productId);
        final CartItem cartItem = cartItemRepository.findCartItemByUsernameAndProduct(username, product)
                .map(CartItem::increaseQuantity)
                .orElseGet(() -> new CartItem(username, product));
        return cartItemRepository.save(cartItem);
    }

    @Override
    @Transactional
    public void decreaseCartItemQuantity(String username, String productId) {
        productManager.getProduct(productId)
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
        productManager.getProduct(productId)
                .flatMap(product -> cartItemRepository.findCartItemByUsernameAndProduct(username, product))
                .ifPresent(cartItemRepository::delete);
    }

    @Override
    @Transactional
    public void clearCart(String username) {
        cartItemRepository.deleteAll(cartItemRepository.findCartItemsByUsername(username));
    }
}
