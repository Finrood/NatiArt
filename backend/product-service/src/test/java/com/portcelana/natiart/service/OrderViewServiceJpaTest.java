package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerOrderItem;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.model.Personalization;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.model.support.PersonalizationOption;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentRepository;
import com.portcelana.natiart.repository.ProductRepository;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import({OrderManagerImpl.class, OrderViewService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderViewServiceJpaTest {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OrderViewService orderViewService;

    @Autowired
    private OrderManager orderManager;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private OrderCreationService orderCreationService;

    @Test
    void customerCanReadSnapshotAndPersonalizationAfterSessionCloses() {
        final TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        final String orderId = transactions.execute(ignored -> {
            final Category category = categoryRepository.save(new Category("orders"));
            final Product product =
                    productRepository.save(new Product("Original vase", new BigDecimal("20.00")).setCategory(category));
            final CustomerOrder order = newOrder(OrderStatus.PENDING);
            order.addOrderItem(new CustomerOrderItem()
                    .setProduct(product)
                    .setProductLabel("Original vase")
                    .setProductSku(product.getId())
                    .setQuantity(1)
                    .setPrice(new BigDecimal("20.00"))
                    .setPersonalization(new Personalization()
                            .setPersonalizationOptions(Map.of(PersonalizationOption.GOLDEN_BORDER, "yes"))));
            final String savedId = orderRepository.saveAndFlush(order).getId();
            paymentRepository.save(new Payment("pay-1", "cus_MINE", savedId));
            return savedId;
        });

        transactions.executeWithoutResult(ignored -> {
            final Product product = productRepository.findAll().getFirst();
            product.setLabel("Renamed catalog vase");
        });

        final OrderDto detail = orderViewService.getCustomerOrder(orderId, "cus_MINE");
        assertEquals(OrderStatus.PENDING, detail.getStatus());
        assertEquals("pay-1", detail.getPaymentId());
        assertEquals("Original vase", detail.getItems().getFirst().getProductLabel());
        assertEquals(
                "yes",
                detail.getItems()
                        .getFirst()
                        .getPersonalization()
                        .getPersonalizationOptions()
                        .get(PersonalizationOption.GOLDEN_BORDER));
        assertEquals(
                orderId,
                orderViewService.getCustomerOrders("cus_MINE", 0, 20).getFirst().getId());
        assertThrows(ResourceNotFoundException.class, () -> orderViewService.getCustomerOrder(orderId, "cus_OTHER"));
        assertTrue(orderViewService.getCustomerOrders("cus_OTHER", 0, 20).isEmpty());

        orderManager.markOrderPaid(orderId);
        final OrderDto paid = orderViewService.getCustomerOrder(orderId, "cus_MINE");
        assertEquals(OrderStatus.PAID, paid.getStatus());
        assertEquals("pay-1", paid.getPaymentId());
    }

    @Test
    void fulfillmentAdvancesVersionAndRejectsStaleWriter() {
        final TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        final String orderId = transactions.execute(ignored ->
                orderRepository.saveAndFlush(newOrder(OrderStatus.PAID)).getId());
        final CustomerOrder stale = transactions.execute(
                ignored -> orderRepository.findById(orderId).orElseThrow());

        final OrderDto updated = orderViewService.advanceFulfillmentStatus(orderId, OrderStatus.PROCESSING);

        assertEquals(OrderStatus.PROCESSING, updated.getStatus());
        final Long newVersion = transactions.execute(
                ignored -> orderRepository.findById(orderId).orElseThrow().getVersion());
        assertTrue(newVersion > stale.getVersion());
        stale.setStatus(OrderStatus.CANCELLED);
        assertThrows(
                OptimisticLockingFailureException.class,
                () -> transactions.executeWithoutResult(ignored -> orderRepository.saveAndFlush(stale)));
    }

    private CustomerOrder newOrder(OrderStatus status) {
        return new CustomerOrder()
                .setFirstname("Buyer")
                .setLastname("Example")
                .setEmail("buyer@example.com")
                .setOrderDate(Instant.now())
                .setOwnerExternalId("cus_MINE")
                .setDeliveryAmount(BigDecimal.ZERO)
                .setTotalAmount(new BigDecimal("20.00"))
                .setStatus(status);
    }
}
