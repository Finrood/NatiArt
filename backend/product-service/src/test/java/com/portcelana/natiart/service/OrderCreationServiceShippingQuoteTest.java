package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.ShippingQuote;
import com.portcelana.natiart.model.ShippingQuoteItem;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class OrderCreationServiceShippingQuoteTest {
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductManager productManager;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ShippingQuoteService shippingQuoteService;

    @Test
    void createOrder_usesQuoteSnapshotAndIgnoresClientDeliveryAmount() {
        final Product product = new Product("Plate", new BigDecimal("9.00"));
        final ShippingQuote quote = new ShippingQuote()
                .setOwnerExternalId("owner-1")
                .setDestinationPostalCode("01001000")
                .setServiceId("correios-pac")
                .setServiceName("PAC")
                .setItemAmount(new BigDecimal("20.00"))
                .setShippingAmount(new BigDecimal("12.50"))
                .setTotalAmount(new BigDecimal("32.50"))
                .setExpiresAt(Instant.parse("2030-01-01T00:15:00Z"))
                .setItems(List.of(new ShippingQuoteItem("p1", 2, new BigDecimal("10.00"), 0)));
        when(productManager.getProductsOrDie(List.of("p1"))).thenReturn(Map.of("p1", product));
        when(shippingQuoteService.requireQuoteForOrder(any(), any(), any(), anyList(), any()))
                .thenReturn(quote);
        when(productRepository.decreaseStockIfAvailable(any(), any(Integer.class)))
                .thenReturn(1);
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final CustomerOrder order = new OrderCreationService(
                        orderRepository, productManager, productRepository, shippingQuoteService)
                .createOrder(
                        new OrderDto()
                                .setFirstname("Ada")
                                .setLastname("Lovelace")
                                .setEmail("ada@example.test")
                                .setZipCode("01001-000")
                                .setItems(List.of(
                                        new OrderItemDto().setProductId("p1").setQuantity(2)))
                                .setDeliveryAmount(BigDecimal.ZERO)
                                .setShippingQuoteId("quote-1"),
                        "owner-1",
                        "key-1",
                        "fingerprint");

        assertEquals(new BigDecimal("12.50"), order.getDeliveryAmount());
        assertEquals(new BigDecimal("32.50"), order.getTotalAmount());
        assertEquals(quote.getId(), order.getShippingQuoteId());
        assertEquals("correios-pac", order.getShippingServiceId());
        assertEquals("01001000", order.getShippingDestinationPostalCode());
        assertEquals(Instant.parse("2030-01-01T00:15:00Z"), order.getShippingQuoteExpiresAt());
        assertEquals(new BigDecimal("10.00"), order.getItems().get(0).getPrice());
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }
}
