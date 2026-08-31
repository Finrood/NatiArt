package com.portcelana.natiart.service;

import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerOrderItem;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderManagerImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductManager productManager;

    @Mock
    private ProductRepository productRepository;

    private OrderManagerImpl orderManager;

    @BeforeEach
    void setUp() {
        orderManager = new OrderManagerImpl(orderRepository, productManager, productRepository);
    }

    private Product product(String id, String label, BigDecimal original, BigDecimal marked, int stock) {
        Product p = new Product(label, original);
        p.setMarkedPrice(marked);
        return p;
    }

    private OrderItemDto item(String productId, Integer quantity) {
        return new OrderItemDto().setProductId(productId).setQuantity(quantity);
    }

    @Test
    void createOrderComputesTotalsAndPersistsItems() {
        Product plate = product("p1", "Plate", new BigDecimal("15.00"), new BigDecimal("13.00"), 100);
        when(productManager.getProductOrDie("p1")).thenReturn(plate);
        when(productRepository.decreaseStockIfAvailable(anyString(), anyInt())).thenReturn(1);
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto dto = new OrderDto()
                .setDeliveryAmount(new BigDecimal("5.00"))
                .setItems(List.of(item("p1", 2)));

        CustomerOrder saved = orderManager.createOrder(dto);

        assertEquals(new BigDecimal("31.00"), saved.getTotalAmount());
        assertEquals(1, saved.getItems().size());
        CustomerOrderItem line = saved.getItems().get(0);
        assertEquals(plate.getId(), line.getProduct().getId());
        assertEquals(2, line.getQuantity());
        assertEquals(new BigDecimal("13.00"), line.getPrice());
        verify(orderRepository).save(any(CustomerOrder.class));
    }

    @Test
    void createOrderRejectsNonPositiveQuantity() {
        OrderDto dto = new OrderDto()
                .setDeliveryAmount(BigDecimal.ONE)
                .setItems(List.of(item("p1", -3)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto));
        verify(productRepository, never()).decreaseStockIfAvailable(any(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderRejectsNegativeDeliveryAmount() {
        OrderDto dto = new OrderDto()
                .setDeliveryAmount(new BigDecimal("-1"))
                .setItems(List.of(item("p1", 1)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderRejectsWhenStockUnavailable() {
        Product mug = product("p2", "Mug", new BigDecimal("10.00"), null, 1);
        when(productManager.getProductOrDie("p2")).thenReturn(mug);
        when(productRepository.decreaseStockIfAvailable(anyString(), anyInt())).thenReturn(0);

        OrderDto dto = new OrderDto()
                .setDeliveryAmount(BigDecimal.ZERO)
                .setItems(List.of(item("p2", 50)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderFallsBackToOriginalPriceWhenNoMarkedPrice() {
        Product vase = product("p3", "Vase", new BigDecimal("25.99"), null, 10);
        when(productManager.getProductOrDie("p3")).thenReturn(vase);
        when(productRepository.decreaseStockIfAvailable(anyString(), anyInt())).thenReturn(1);
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto dto = new OrderDto()
                .setDeliveryAmount(BigDecimal.ZERO)
                .setItems(List.of(item("p3", 1)));

        CustomerOrder saved = orderManager.createOrder(dto);
        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(new BigDecimal("25.99"), captor.getValue().getItems().get(0).getPrice());
        assertEquals(saved, captor.getValue());
    }

    @Test
    void createOrderFailsWholeOrderWhenAnyLineHasInsufficientStock() {
        // First line is fine, second line runs out of stock: the whole order must be aborted
        // (no CustomerOrder persisted) instead of persisting a partial order with one decrement.
        Product plate = product("p1", "Plate", new BigDecimal("15.00"), null, 100);
        Product mug = product("p2", "Mug", new BigDecimal("10.00"), null, 1);
        when(productManager.getProductOrDie("p1")).thenReturn(plate);
        when(productManager.getProductOrDie("p2")).thenReturn(mug);
        // Product ids are auto-generated, so stub the atomic decrement by invocation order:
        // 1st line succeeds, 2nd line is refused.
        when(productRepository.decreaseStockIfAvailable(anyString(), anyInt())).thenReturn(1, 0);

        OrderDto dto = new OrderDto()
                .setDeliveryAmount(BigDecimal.ZERO)
                .setItems(List.of(item("p1", 1), item("p2", 50)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto));
        // Stock was attempted for both lines (the first decrements, the second is refused)...
        verify(productRepository, times(2)).decreaseStockIfAvailable(anyString(), anyInt());
        // ...but nothing was persisted: the @Transactional boundary rolls the whole order back.
        verify(orderRepository, never()).save(any());
    }
}
