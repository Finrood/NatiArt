package com.portcelana.natiart.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.portcelana.natiart.controller.OrderController;
import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.service.OrderCreationService;
import com.portcelana.natiart.service.OrderManagerImpl;
import com.portcelana.natiart.service.ProductManager;
import com.portcelana.natiart.service.ShippingService;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class OrderReplayIntegrationTest {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void controllerReplayReturnsTheSameItemsAndReservesStockOnce() {
        final Product product = seedProduct();
        final OrderController controller = new OrderController(realManager(product));
        final OrderDto request = orderRequest(product.getId());
        final AuthenticationResponseDto.Principal principal = principal();

        final OrderDto created = controller.createOrder(request, "checkout-1", principal);
        entityManager.flush();
        entityManager.clear();

        final OrderDto replayed = controller.createOrder(request, "checkout-1", principal);

        assertSameResponse(created, replayed);
        assertEquals(
                4, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void raceLoserReloadsThePersistedWinnerWithItemsWithoutAnotherReservation() {
        final Product product = seedProduct();
        final OrderDto request = orderRequest(product.getId());
        final AuthenticationResponseDto.Principal principal = principal();
        final OrderDto winner = new OrderController(realManager(product)).createOrder(request, "checkout-1", principal);
        entityManager.flush();
        entityManager.clear();

        final OrderRepository racingRepository = mock(OrderRepository.class);
        when(racingRepository.findByOwnerExternalIdAndIdempotencyKey("cus_jane", "checkout-1"))
                .thenReturn(Optional.empty())
                .thenAnswer(
                        invocation -> orderRepository.findByOwnerExternalIdAndIdempotencyKey("cus_jane", "checkout-1"));
        final OrderCreationService losingCreation = mock(OrderCreationService.class);
        when(losingCreation.createOrder(any(OrderDto.class), eq("cus_jane"), eq("checkout-1"), anyString()))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        final OrderDto replayed = new OrderController(new OrderManagerImpl(racingRepository, losingCreation))
                .createOrder(request, "checkout-1", principal);

        assertSameResponse(winner, replayed);
        assertEquals(
                4, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
        verify(racingRepository, times(2)).findByOwnerExternalIdAndIdempotencyKey("cus_jane", "checkout-1");
        verify(losingCreation).createOrder(any(OrderDto.class), eq("cus_jane"), eq("checkout-1"), anyString());
    }

    private Product seedProduct() {
        final Category category = categoryRepository.save(new Category("plates"));
        return productRepository.save(new Product("Handmade plate", new BigDecimal("25.00"))
                .setCategory(category)
                .setStockQuantity(5));
    }

    private OrderManagerImpl realManager(Product product) {
        final ProductManager productManager = mock(ProductManager.class);
        when(productManager.getProductsOrDie(List.of(product.getId()))).thenReturn(Map.of(product.getId(), product));
        final ShippingService shippingService = mock(ShippingService.class);
        when(shippingService.getOrderShippingAmount("01001000")).thenReturn(BigDecimal.ZERO);
        return new OrderManagerImpl(
                orderRepository,
                new OrderCreationService(orderRepository, productManager, productRepository, shippingService));
    }

    private OrderDto orderRequest(String productId) {
        return new OrderDto()
                .setHouseNumber("N/A")
                .setFirstname("Jane")
                .setLastname("Customer")
                .setEmail("jane@example.com")
                .setZipCode("01001000")
                .setItems(List.of(new OrderItemDto().setProductId(productId).setQuantity(1)));
    }

    private AuthenticationResponseDto.Principal principal() {
        final AuthenticationResponseDto.Principal principal = mock(AuthenticationResponseDto.Principal.class);
        when(principal.getExternalId()).thenReturn("cus_jane");
        return principal;
    }

    private void assertSameResponse(OrderDto first, OrderDto second) {
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getItems().size(), second.getItems().size());
        assertEquals(
                first.getItems().getFirst().getId(),
                second.getItems().getFirst().getId());
        assertEquals(
                first.getItems().getFirst().getProductId(),
                second.getItems().getFirst().getProductId());
        assertEquals(
                first.getItems().getFirst().getQuantity(),
                second.getItems().getFirst().getQuantity());
    }
}
