package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerOrderItem;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;

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

    private OrderDto validOrder() {
        return new OrderDto().setFirstname("Test").setLastname("Customer").setEmail("customer@example.com");
    }

    @Test
    void createOrderComputesTotalsAndPersistsItems() {
        Product plate = product("p1", "Plate", new BigDecimal("15.00"), new BigDecimal("13.00"), 100);
        when(productManager.getProductsOrDie(List.of("p1"))).thenReturn(Map.of("p1", plate));
        when(productRepository.decreaseStockIfAvailable(anyString(), anyInt())).thenReturn(1);
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto dto = validOrder().setDeliveryAmount(new BigDecimal("5.00")).setItems(List.of(item("p1", 2)));

        CustomerOrder saved = orderManager.createOrder(dto, "user-1");

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
        OrderDto dto = validOrder().setDeliveryAmount(BigDecimal.ONE).setItems(List.of(item("p1", -3)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto, "user-1"));
        verify(productRepository, never()).decreaseStockIfAvailable(any(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderRejectsNegativeDeliveryAmount() {
        OrderDto dto = validOrder().setDeliveryAmount(new BigDecimal("-1")).setItems(List.of(item("p1", 1)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto, "user-1"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderRejectsWhenStockUnavailable() {
        Product mug = product("p2", "Mug", new BigDecimal("10.00"), null, 1);
        when(productManager.getProductsOrDie(List.of("p2"))).thenReturn(Map.of("p2", mug));
        when(productRepository.decreaseStockIfAvailable(anyString(), anyInt())).thenReturn(0);

        OrderDto dto = validOrder().setDeliveryAmount(BigDecimal.ZERO).setItems(List.of(item("p2", 50)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto, "user-1"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderFallsBackToOriginalPriceWhenNoMarkedPrice() {
        Product vase = product("p3", "Vase", new BigDecimal("25.99"), null, 10);
        when(productManager.getProductsOrDie(List.of("p3"))).thenReturn(Map.of("p3", vase));
        when(productRepository.decreaseStockIfAvailable(anyString(), anyInt())).thenReturn(1);
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto dto = validOrder().setDeliveryAmount(BigDecimal.ZERO).setItems(List.of(item("p3", 1)));

        CustomerOrder saved = orderManager.createOrder(dto, "user-1");
        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(
                new BigDecimal("25.99"), captor.getValue().getItems().get(0).getPrice());
        assertEquals(saved, captor.getValue());
    }

    @Test
    void createOrderRejectsQuantityAboveCap() {
        OrderDto dto = validOrder().setDeliveryAmount(BigDecimal.ONE).setItems(List.of(item("p1", 101)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto, "user-1"));
        verify(productRepository, never()).decreaseStockIfAvailable(any(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderRejectsTooManyLines() {
        final List<OrderItemDto> items = new java.util.ArrayList<>();
        for (int i = 0; i < 51; i++) {
            items.add(item("p" + i, 1));
        }
        OrderDto dto = validOrder().setDeliveryAmount(BigDecimal.ONE).setItems(items);

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto, "user-1"));
        verify(productRepository, never()).decreaseStockIfAvailable(anyString(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderRejectsInactiveProduct() {
        Product retired = product("p4", "Retired plate", new BigDecimal("15.00"), null, 100)
                .setActive(false);
        when(productManager.getProductsOrDie(List.of("p4"))).thenReturn(Map.of("p4", retired));

        OrderDto dto = validOrder().setDeliveryAmount(BigDecimal.ZERO).setItems(List.of(item("p4", 1)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto, "user-1"));
        verify(productRepository, never()).decreaseStockIfAvailable(any(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderFailsWholeOrderWhenAnyLineHasInsufficientStock() {
        // First line is fine, second line runs out of stock: the whole order must be aborted
        // (no CustomerOrder persisted) instead of persisting a partial order with one decrement.
        Product plate = product("p1", "Plate", new BigDecimal("15.00"), null, 100);
        Product mug = product("p2", "Mug", new BigDecimal("10.00"), null, 1);
        when(productManager.getProductsOrDie(List.of("p1", "p2"))).thenReturn(Map.of("p1", plate, "p2", mug));
        // Product ids are auto-generated, so stub the atomic decrement by invocation order:
        // 1st line succeeds, 2nd line is refused.
        when(productRepository.decreaseStockIfAvailable(anyString(), anyInt())).thenReturn(1, 0);

        OrderDto dto = validOrder().setDeliveryAmount(BigDecimal.ZERO).setItems(List.of(item("p1", 1), item("p2", 50)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto, "user-1"));
        // Stock was attempted for both lines (the first decrements, the second is refused)...
        verify(productRepository, times(2)).decreaseStockIfAvailable(anyString(), anyInt());
        // ...but nothing was persisted: the @Transactional boundary rolls the whole order back.
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderBatchesProductReadsIntoOneQuery() {
        Product plate = product("p1", "Plate", new BigDecimal("15.00"), null, 100);
        Product mug = product("p2", "Mug", new BigDecimal("10.00"), null, 100);
        Product vase = product("p3", "Vase", new BigDecimal("25.00"), null, 100);
        when(productManager.getProductsOrDie(List.of("p1", "p2", "p3")))
                .thenReturn(Map.of("p1", plate, "p2", mug, "p3", vase));
        when(productRepository.decreaseStockIfAvailable(anyString(), anyInt())).thenReturn(1);
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto dto = validOrder()
                .setDeliveryAmount(BigDecimal.ZERO)
                .setItems(List.of(item("p1", 1), item("p2", 1), item("p3", 1)));

        CustomerOrder saved = orderManager.createOrder(dto, "user-1");

        assertEquals(3, saved.getItems().size());
        verify(productManager, times(1)).getProductsOrDie(List.of("p1", "p2", "p3"));
        verify(productRepository, times(3)).decreaseStockIfAvailable(anyString(), anyInt());
        verify(orderRepository).save(any(CustomerOrder.class));
    }

    @Test
    void createOrderUnknownProductId_throwsNotFoundWithoutDecrements() {
        when(productManager.getProductsOrDie(List.of("missing")))
                .thenThrow(new ResourceNotFoundException("Product with id [missing] not found"));

        OrderDto dto = validOrder().setDeliveryAmount(BigDecimal.ZERO).setItems(List.of(item("missing", 1)));

        assertThrows(ResourceNotFoundException.class, () -> orderManager.createOrder(dto, "user-1"));
        verify(productRepository, never()).decreaseStockIfAvailable(anyString(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderPersistsOwnerFromAuthenticatedPrincipal() {
        Product plate = product("p1", "Plate", new BigDecimal("15.00"), new BigDecimal("13.00"), 100);
        when(productManager.getProductsOrDie(List.of("p1"))).thenReturn(Map.of("p1", plate));
        when(productRepository.decreaseStockIfAvailable(anyString(), anyInt())).thenReturn(1);
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto dto = validOrder().setDeliveryAmount(new BigDecimal("5.00")).setItems(List.of(item("p1", 2)));

        CustomerOrder saved = orderManager.createOrder(dto, "user-1");

        assertEquals("user-1", saved.getOwnerExternalId());
        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderRepository).save(captor.capture());
        assertEquals("user-1", captor.getValue().getOwnerExternalId());
    }

    @Test
    void createOrderRejectsBlankOwnerWithoutTouchingStock() {
        OrderDto dto = validOrder().setDeliveryAmount(BigDecimal.ONE).setItems(List.of(item("p1", 1)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto, "  "));
        verify(productRepository, never()).decreaseStockIfAvailable(any(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderRejectsNullOwnerWithoutTouchingStock() {
        OrderDto dto = validOrder().setDeliveryAmount(BigDecimal.ONE).setItems(List.of(item("p1", 1)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto, null));
        verify(productRepository, never()).decreaseStockIfAvailable(any(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrderRejectsMissingContactDetailsBeforeReservingStock() {
        OrderDto dto = new OrderDto()
                .setFirstname(null)
                .setLastname("Customer")
                .setEmail("customer@example.com")
                .setDeliveryAmount(BigDecimal.ZERO)
                .setItems(List.of(item("p1", 1)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.createOrder(dto, "user-1"));
        verify(productRepository, never()).decreaseStockIfAvailable(any(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrderStatus_allowedTransition_updatesWithoutEntitySave() {
        final CustomerOrder order = new CustomerOrder().setStatus(OrderStatus.PENDING);
        final String orderId = order.getId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        // Simulate the bulk update landing: the post-update re-read observes PAID.
        when(orderRepository.updateStatusById(orderId, OrderStatus.PAID)).thenAnswer(invocation -> {
            order.setStatus(OrderStatus.PAID);
            return 1;
        });

        final CustomerOrder updated = orderManager.updateOrderStatus(orderId, OrderStatus.PAID);

        assertEquals(orderId, updated.getId());
        assertEquals(OrderStatus.PAID, updated.getStatus());
        verify(orderRepository).updateStatusById(orderId, OrderStatus.PAID);
        // Guard read plus post-update re-read: pre-fix code reads once.
        verify(orderRepository, times(2)).findById(orderId);
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void updateOrderStatus_terminalTransition_throwsWithoutUpdate() {
        final CustomerOrder order = new CustomerOrder().setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThrows(
                IllegalArgumentException.class, () -> orderManager.updateOrderStatus(order.getId(), OrderStatus.PAID));

        verify(orderRepository, never()).updateStatusById(anyString(), any());
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void updateOrderStatus_skippedStage_throwsWithoutUpdate() {
        final CustomerOrder order = new CustomerOrder().setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThrows(
                IllegalArgumentException.class,
                () -> orderManager.updateOrderStatus(order.getId(), OrderStatus.SHIPPED));

        verify(orderRepository, never()).updateStatusById(anyString(), any());
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void updateOrderStatus_missingOrder_throwsNotFound() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class, () -> orderManager.updateOrderStatus("missing", OrderStatus.PAID));

        verify(orderRepository, never()).updateStatusById(anyString(), any());
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }
}
