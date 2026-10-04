package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.ShippingQuote;
import com.portcelana.natiart.model.ShippingQuoteItem;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.OrderReservationOwnerRepository;
import com.portcelana.natiart.repository.ProductRepository;

@DataJpaTest(properties = {"spring.sql.init.mode=never", "natiart.test.contract=OrderReservationBudgetTest"})
@Import({OrderManagerImpl.class, OrderCreationService.class})
class OrderReservationBudgetTest {
    @Autowired
    private OrderReservationOwnerRepository reservationOwners;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderManager manager;

    @MockitoSpyBean
    private OrderRepository orders;

    @MockitoSpyBean
    private ProductRepository products;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private PlatformTransactionManager transactions;

    @MockitoBean
    private AsaasChargeSafetyService chargeSafety;

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
                .setCountry("Brazil")
                .setState("SP")
                .setCity("City")
                .setNeighborhood("Area")
                .setStreet("Street")
                .setHouseNumber("N/A")
                .setItems(items);
    }

    private void seedPending(String owner) {
        for (int i = 0; i < 4; i++) {
            orders.saveAndFlush(new CustomerOrder()
                    .setFirstname("Fixture")
                    .setLastname("Customer")
                    .setEmail("cap@example.test")
                    .setOwnerExternalId(owner)
                    .setOrderDate(Instant.now())
                    .setStatus(OrderStatus.PENDING)
                    .setDeliveryAmount(BigDecimal.ZERO)
                    .setTotalAmount(BigDecimal.TEN));
        }
    }

    private boolean reserve(String owner, String product, String key) {
        try {
            manager.createOrder(
                    request(List.of(new OrderItemDto().setProductId(product).setQuantity(1))), owner, key);
            return true;
        } catch (OrderCreationRejectedException rejected) {
            assertTrue(
                    rejected.getCause().getMessage().contains("Too many unpaid orders"),
                    rejected.getCause()::getMessage);
            return false;
        }
    }

    private long pendingCount(String owner) {
        return entityManager
                .createQuery(
                        "SELECT COUNT(o) FROM CustomerOrder o WHERE o.ownerExternalId = :owner AND o.status = :status",
                        Long.class)
                .setParameter("owner", owner)
                .setParameter("status", OrderStatus.PENDING)
                .getSingleResult();
    }

    private int stock(String product) {
        return transaction.execute(
                status -> products.findById(product).orElseThrow().getStockQuantity());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFifthReservationsSerializeByOwnerAndRejectedWorkConsumesNoStock() throws Exception {
        final String owner = "cap-owner-" + UUID.randomUUID();
        seedPending(owner);
        final String firstProduct = seed(10);
        final String secondProduct = seed(10);
        final CountDownLatch bothStarted = new CountDownLatch(2);
        final CountDownLatch bothCounted = new CountDownLatch(2);
        doAnswer(invocation -> {
                    final long count = pendingCount(invocation.getArgument(0));
                    bothCounted.countDown();
                    // Unlocked creators both observe four; a locked creator can proceed alone.
                    bothCounted.await(1, TimeUnit.SECONDS);
                    return count;
                })
                .when(orders)
                .countByOwnerExternalIdAndStatus(owner, OrderStatus.PENDING);
        try (final var workers = Executors.newFixedThreadPool(2)) {
            final var first = workers.submit(() -> {
                bothStarted.countDown();
                assertTrue(bothStarted.await(5, TimeUnit.SECONDS));
                return reserve(owner, firstProduct, "fifth-one");
            });
            final var second = workers.submit(() -> {
                bothStarted.countDown();
                assertTrue(bothStarted.await(5, TimeUnit.SECONDS));
                return reserve(owner, secondProduct, "fifth-two");
            });
            final boolean firstSucceeded = first.get(15, TimeUnit.SECONDS);
            final boolean secondSucceeded = second.get(15, TimeUnit.SECONDS);
            assertEquals(5, orders.countByOwnerExternalIdAndStatus(owner, OrderStatus.PENDING));
            assertEquals(1, (firstSucceeded ? 1 : 0) + (secondSucceeded ? 1 : 0));
            assertEquals(firstSucceeded ? 9 : 10, stock(firstProduct));
            assertEquals(secondSucceeded ? 9 : 10, stock(secondProduct));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void differentOwnersCanReserveWhileAnotherOwnerHoldsItsBudgetLock() throws Exception {
        final String owner = "held-owner-" + UUID.randomUUID();
        final String other = "other-owner-" + UUID.randomUUID();
        final String firstProduct = seed(10);
        final String secondProduct = seed(10);
        final CountDownLatch firstCounted = new CountDownLatch(1);
        final CountDownLatch secondCommitted = new CountDownLatch(1);
        doAnswer(invocation -> {
                    final long count = pendingCount(invocation.getArgument(0));
                    firstCounted.countDown();
                    assertTrue(
                            secondCommitted.await(5, TimeUnit.SECONDS),
                            "A different account must not wait for this account's budget");
                    return count;
                })
                .when(orders)
                .countByOwnerExternalIdAndStatus(owner, OrderStatus.PENDING);
        try (final var workers = Executors.newFixedThreadPool(2)) {
            final var first = workers.submit(() -> reserve(owner, firstProduct, "held"));
            assertTrue(firstCounted.await(5, TimeUnit.SECONDS));
            final var second = workers.submit(() -> {
                final boolean result = reserve(other, secondProduct, "independent");
                secondCommitted.countDown();
                return result;
            });
            assertTrue(second.get(10, TimeUnit.SECONDS));
            assertTrue(first.get(10, TimeUnit.SECONDS));
            assertEquals(9, stock(firstProduct));
            assertEquals(9, stock(secondProduct));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollbackAndCommittedCancellationReleaseBudgetWithoutCounterDrift() {
        final String owner = "rollback-budget-" + UUID.randomUUID();
        seedPending(owner);
        final String product = seed(0);
        assertThrows(
                OrderCreationRejectedException.class,
                () -> manager.createOrder(
                        request(List.of(new OrderItemDto().setProductId(product).setQuantity(1))), owner, "retry-key"));
        assertEquals(4, orders.countByOwnerExternalIdAndStatus(owner, OrderStatus.PENDING));
        assertTrue(reservationOwners.existsById(owner), "Independent owner initialization survives order rollback");
        transaction.executeWithoutResult(
                status -> products.findById(product).orElseThrow().setStockQuantity(2));
        final CustomerOrder fifth = manager.createOrder(
                request(List.of(new OrderItemDto().setProductId(product).setQuantity(1))), owner, "retry-key");
        assertEquals(5, orders.countByOwnerExternalIdAndStatus(owner, OrderStatus.PENDING));
        manager.cancelPendingOrder(fifth.getId(), owner);
        assertEquals(4, orders.countByOwnerExternalIdAndStatus(owner, OrderStatus.PENDING));
        assertEquals(2, stock(product));
        assertTrue(reserve(owner, product, "replacement"));
        assertEquals(5, orders.countByOwnerExternalIdAndStatus(owner, OrderStatus.PENDING));
        assertEquals(1, stock(product));
    }
}
