package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.portcelana.natiart.controller.helper.OrderCreationRejectedException;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.OrderItemDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.ShippingQuote;
import com.portcelana.natiart.model.ShippingQuoteItem;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;

@DataJpaTest(properties = {"spring.sql.init.mode=never", "natiart.test.contract=OrderCommittedContractTest"})
@Import({OrderManagerImpl.class, OrderCreationService.class})
class OrderCommittedContractTest {
    @Autowired
    private OrderManager manager;

    @Autowired
    private OrderRepository orders;

    @MockitoSpyBean
    private ProductRepository products;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private PlatformTransactionManager transactions;

    @MockitoBean
    private ProductManager productManager;

    @MockitoBean
    private ShippingQuoteService shipping;

    @MockitoBean
    private CustomerUploadService uploads;

    private TransactionTemplate transaction;

    @BeforeEach
    void configure() {
        transaction = new TransactionTemplate(transactions);
        when(productManager.getProductsOrDie(anyList()))
                .thenAnswer(invocation -> products.findAllById(invocation.<List<String>>getArgument(0)).stream()
                        .collect(Collectors.toMap(Product::getId, product -> product)));
        when(shipping.requireQuoteForOrder(any(), any(), any(), anyList(), any()))
                .thenAnswer(invocation -> {
                    final List<OrderItemDto> items = invocation.getArgument(3);
                    final java.util.Map<String, Product> byId = invocation.getArgument(4);
                    final List<ShippingQuoteItem> quoted = items.stream()
                            .map(item -> {
                                final Product product = byId.get(item.getProductId());
                                return new ShippingQuoteItem(
                                        product.getId(),
                                        item.getQuantity(),
                                        product.getOriginalPrice(),
                                        product.getVersion());
                            })
                            .toList();
                    final BigDecimal total = quoted.stream()
                            .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new ShippingQuote()
                            .setItems(quoted)
                            .setShippingAmount(BigDecimal.ZERO)
                            .setItemAmount(total)
                            .setTotalAmount(total)
                            .setServiceId("fixture")
                            .setDestinationPostalCode("01001000")
                            .setExpiresAt(Instant.now().plusSeconds(900));
                });
    }

    private String seed(int stock) {
        return transaction.execute(status -> {
            final Category category = categories.save(new Category("contract-" + UUID.randomUUID()));
            return products.save(new Product("Handmade contract product", new BigDecimal("10.00"))
                            .setCategory(category)
                            .setStockQuantity(stock))
                    .getId();
        });
    }

    private OrderDto request(List<OrderItemDto> items) {
        return new OrderDto()
                .setFirstname("Fixture")
                .setLastname("Customer")
                .setEmail("contract@example.test")
                .setZipCode("01001000")
                .setHouseNumber("N/A")
                .setItems(items);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void committedReplayMapsDetachedItemsAndDoesNotReserveStockTwice() {
        final String id = seed(5);
        final String owner = "contract-owner-" + UUID.randomUUID();
        final OrderDto request =
                request(List.of(new OrderItemDto().setProductId(id).setQuantity(2)));
        final OrderDto first = OrderDto.from(manager.createOrder(request, owner, "committed-key"));
        final OrderDto replay = OrderDto.from(manager.createOrder(request, owner, "committed-key"));
        assertEquals(first.getId(), replay.getId());
        assertEquals(1, replay.getItems().size());
        assertEquals(2, replay.getItems().getFirst().getQuantity());
        assertEquals(id, replay.getItems().getFirst().getProductId());
        assertEquals(
                3,
                transaction
                        .<Integer>execute(
                                status -> products.findById(id).orElseThrow().getStockQuantity())
                        .intValue());
        assertEquals(
                1,
                orders.findAll().stream()
                        .filter(order -> owner.equals(order.getOwnerExternalId()))
                        .count());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failureOnLaterLineRollsBackEarlierDatabaseStockReservationAndOrderInsert() {
        final List<String> ids =
                java.util.stream.Stream.of(seed(5), seed(5)).sorted().toList();
        final String first = ids.getFirst();
        final String second = ids.getLast();
        transaction.executeWithoutResult(
                status -> products.findById(second).orElseThrow().setStockQuantity(0));
        final String owner = "rollback-owner-" + UUID.randomUUID();
        final OrderCreationRejectedException failure = assertThrows(
                OrderCreationRejectedException.class,
                () -> manager.createOrder(
                        request(List.of(
                                new OrderItemDto().setProductId(first).setQuantity(2),
                                new OrderItemDto().setProductId(second).setQuantity(1))),
                        owner,
                        "rollback-key"));
        assertNotNull(failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("Insufficient stock"), failure.getCause()::getMessage);
        final InOrder reservationOrder = inOrder(products);
        reservationOrder.verify(products).decreaseStockIfAvailable(first, 2);
        reservationOrder.verify(products).decreaseStockIfAvailable(second, 1);
        assertEquals(
                5,
                transaction
                        .<Integer>execute(
                                status -> products.findById(first).orElseThrow().getStockQuantity())
                        .intValue());
        assertTrue(orders.findByOwnerExternalIdAndIdempotencyKey(owner, "rollback-key")
                .isEmpty());
    }
}
