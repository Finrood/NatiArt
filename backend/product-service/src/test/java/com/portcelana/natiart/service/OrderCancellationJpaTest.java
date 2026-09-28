package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerOrderItem;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.model.PaymentIdempotency;
import com.portcelana.natiart.model.PaymentIdempotencyStatus;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentIdempotencyRepository;
import com.portcelana.natiart.repository.PaymentRepository;
import com.portcelana.natiart.repository.ProductRepository;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import({OrderManagerImpl.class, PaymentIdempotencyService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderCancellationJpaTest {
    @Autowired
    private OrderManager orderManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentIdempotencyRepository paymentIdempotencyRepository;

    @Autowired
    private PaymentIdempotencyService paymentIdempotencyService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private OrderCreationService orderCreationService;

    @MockitoBean
    private AsaasChargeSafetyService chargeSafetyService;

    @Test
    void verifiedInactivePaymentRowReleasesStockExactlyOnce() {
        final Seed seed = seed(true);

        final com.portcelana.natiart.dto.OrderDto cancelled =
                orderManager.cancelPendingOrderResponse(seed.orderId(), "cus_MINE");
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(2, cancelled.getItems().getFirst().getQuantity());
        assertEquals(
                OrderStatus.CANCELLED,
                orderManager.cancelPendingOrder(seed.orderId(), "cus_MINE").getStatus());

        assertEquals(
                10, productRepository.findById(seed.productId()).orElseThrow().getStockQuantity());
        verify(chargeSafetyService).ensureChargeInactive(any(Payment.class));
    }

    @Test
    void uncertainPaymentRowNeverReleasesStock() {
        final Seed seed = seed(true);
        doThrow(new IllegalStateException("provider uncertain"))
                .when(chargeSafetyService)
                .ensureChargeInactive(any(Payment.class));

        assertThrows(IllegalStateException.class, () -> orderManager.cancelPendingOrder(seed.orderId(), null));

        assertEquals(
                8, productRepository.findById(seed.productId()).orElseThrow().getStockQuantity());
        assertEquals(
                OrderStatus.PENDING,
                orderRepository.findById(seed.orderId()).orElseThrow().getStatus());
    }

    @Test
    void unresolvedAttemptWithoutPaymentRowNeverReleasesStock() {
        final Seed seed = seed(false);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> paymentIdempotencyRepository.save(
                        new PaymentIdempotency("cus_MINE", seed.idempotencyKey(), "fingerprint", seed.orderId())
                                .setStatus(PaymentIdempotencyStatus.FAILED_RECOVERABLE)));

        assertThrows(IllegalArgumentException.class, () -> orderManager.cancelPendingOrder(seed.orderId(), null));

        assertEquals(
                8, productRepository.findById(seed.productId()).orElseThrow().getStockQuantity());
        assertEquals(
                OrderStatus.PENDING,
                orderRepository.findById(seed.orderId()).orElseThrow().getStatus());
    }

    @Test
    void recoverableAttemptWithMatchingPersistedPaymentCanReleaseAfterProviderProof() {
        final Seed seed = seed(true);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> paymentIdempotencyRepository.save(
                        new PaymentIdempotency("cus_MINE", seed.idempotencyKey(), "fingerprint", seed.orderId())
                                .setStatus(PaymentIdempotencyStatus.FAILED_RECOVERABLE)));

        orderManager.cancelPendingOrder(seed.orderId(), null);

        assertEquals(
                10, productRepository.findById(seed.productId()).orElseThrow().getStockQuantity());
        final PaymentIdempotency reconciled = paymentIdempotencyRepository
                .findByOwnerExternalIdAndIdempotencyKey("cus_MINE", seed.idempotencyKey())
                .orElseThrow();
        assertEquals(PaymentIdempotencyStatus.SUCCEEDED, reconciled.getStatus());
        assertEquals(seed.paymentId(), reconciled.getProviderPaymentId());
        verify(chargeSafetyService).ensureChargeInactive(any(Payment.class));
    }

    @Test
    void inProgressAttemptWithPersistedPaymentStillBlocksRelease() {
        final Seed seed = seed(true);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> paymentIdempotencyRepository.save(
                        new PaymentIdempotency("cus_MINE", seed.idempotencyKey(), "fingerprint", seed.orderId())));

        assertThrows(IllegalArgumentException.class, () -> orderManager.cancelPendingOrder(seed.orderId(), null));

        assertEquals(
                8, productRepository.findById(seed.productId()).orElseThrow().getStockQuantity());
    }

    @Test
    void reservationCreatedBeforeExpiryBlocksRelease() {
        final Seed seed = seed(false);

        paymentIdempotencyService.reserve("cus_MINE", seed.idempotencyKey(), "fingerprint", seed.orderId());

        assertThrows(IllegalArgumentException.class, () -> orderManager.cancelPendingOrder(seed.orderId(), null));
        assertEquals(
                8, productRepository.findById(seed.productId()).orElseThrow().getStockQuantity());
    }

    @Test
    void cancelledOrderCannotAcquireAProviderAttempt() {
        final Seed seed = seed(false);
        orderManager.cancelPendingOrder(seed.orderId(), "cus_MINE");

        assertThrows(
                IllegalArgumentException.class,
                () -> paymentIdempotencyService.reserve(
                        "cus_MINE", seed.idempotencyKey(), "fingerprint", seed.orderId()));
        assertEquals(
                10, productRepository.findById(seed.productId()).orElseThrow().getStockQuantity());
    }

    private Seed seed(boolean withPayment) {
        return new TransactionTemplate(transactionManager).execute(ignored -> {
            final Category category = categoryRepository.save(new Category("vases-" + System.nanoTime()));
            final Product product = productRepository.save(new Product("Vase", new BigDecimal("20.00"))
                    .setCategory(category)
                    .setStockQuantity(8));
            final CustomerOrder order = new CustomerOrder()
                    .setFirstname("Buyer")
                    .setLastname("Example")
                    .setEmail("buyer@example.com")
                    .setOwnerExternalId("cus_MINE")
                    .setOrderDate(Instant.now())
                    .setStatus(OrderStatus.PENDING)
                    .setDeliveryAmount(BigDecimal.ZERO)
                    .setTotalAmount(new BigDecimal("40.00"));
            order.addOrderItem(
                    new CustomerOrderItem().setProduct(product).setQuantity(2).setPrice(new BigDecimal("20.00")));
            orderRepository.saveAndFlush(order);
            final String idempotencyKey = "attempt-" + order.getId();
            final String paymentId = withPayment ? "pay_" + java.util.UUID.randomUUID() : null;
            if (withPayment) {
                paymentRepository.save(new Payment(paymentId, "cus_MINE", order.getId(), idempotencyKey));
            }
            return new Seed(order.getId(), product.getId(), paymentId, idempotencyKey);
        });
    }

    private record Seed(String orderId, String productId, String paymentId, String idempotencyKey) {}
}
