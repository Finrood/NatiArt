package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portcelana.natiart.dto.CartItemDto;
import com.portcelana.natiart.model.CartItem;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.CartItemRepository;

@ExtendWith(MockitoExtension.class)
class CartManagerImplTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductManager productManager;

    private CartManagerImpl cartManager;

    @BeforeEach
    void setUp() {
        cartManager = new CartManagerImpl(cartItemRepository, productManager);
    }

    private Product product(String label) {
        return new Product(label, new BigDecimal("10.00"));
    }

    @Test
    void getCartItemsByUsername_roundTripsQuantityAndProduct() {
        final Product product = product("Plate");
        final CartItem first = new CartItem("jane", product);
        first.increaseQuantity().increaseQuantity();
        final CartItem second = new CartItem("jane", product("Cup"));
        when(cartItemRepository.findCartItemsByUsername("jane")).thenReturn(List.of(first, second));

        final List<CartItemDto> result = cartManager.getCartItemsByUsername("jane");

        assertEquals(2, result.size());
        assertEquals(3, result.get(0).getQuantity());
        assertEquals("Plate", result.get(0).getProductDto().getLabel());
        assertEquals(1, result.get(1).getQuantity());
        assertNull(result.get(0).getPersonalizationDto());
    }

    @Test
    void createCartItem_createsNewLineWithQuantityOne() {
        final Product product = product("Plate");
        when(productManager.getProductOrDie("p1")).thenReturn(product);
        when(cartItemRepository.findCartItemByUsernameAndProduct("jane", product))
                .thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

        final CartItemDto result = cartManager.createCartItem("jane", "p1");

        assertEquals(1, result.getQuantity());
        assertEquals("Plate", result.getProductDto().getLabel());
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    void createCartItem_incrementsQuantityOfExistingLine() {
        final Product product = product("Plate");
        final CartItem existing = new CartItem("jane", product);
        when(productManager.getProductOrDie("p1")).thenReturn(product);
        when(cartItemRepository.findCartItemByUsernameAndProduct("jane", product))
                .thenReturn(Optional.of(existing));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

        final CartItemDto result = cartManager.createCartItem("jane", "p1");

        assertEquals(2, result.getQuantity());
    }
}
