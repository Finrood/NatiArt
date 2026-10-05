package com.portcelana.natiart.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.service.PaymentIdempotencyReservation;
import com.portcelana.natiart.service.PaymentIdempotencyService;

/** Proves the database reservation, rather than a JVM lock, serializes contenders. */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import(PaymentIdempotencyService.class)
class PaymentIdempotencyConcurrencyTest {
    @Autowired
    private PaymentIdempotencyService paymentIdempotencyService;

    @Autowired
    private PaymentIdempotencyRepository paymentIdempotencyRepository;

    @Autowired
    private OrderRepository orderRepository;

    @AfterEach
    void cleanCommittedOrders() {
        paymentIdempotencyRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void simultaneousReservationsHaveOneWinnerAndOneUniqueKeyLoser() throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final List<Future<PaymentIdempotencyReservation>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return paymentIdempotencyService.reserve("cus_MINE", "payment-attempt-1", "same-request");
                }));
            }
            start.countDown();

            final List<PaymentIdempotencyReservation> reservations = new ArrayList<>();
            for (Future<PaymentIdempotencyReservation> attempt : attempts) {
                try {
                    reservations.add(attempt.get());
                } catch (ExecutionException e) {
                    assertInstanceOf(DataIntegrityViolationException.class, e.getCause());
                }
            }

            assertEquals(
                    1,
                    reservations.stream()
                            .filter(PaymentIdempotencyReservation::acquired)
                            .count());
            assertEquals(
                    1,
                    paymentIdempotencyRepository
                            .findByOwnerExternalIdAndIdempotencyKey("cus_MINE", "payment-attempt-1")
                            .stream()
                            .count());
            assertEquals(
                    "same-request",
                    reservations.stream()
                            .filter(PaymentIdempotencyReservation::acquired)
                            .findFirst()
                            .orElseThrow()
                            .record()
                            .getRequestFingerprint());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void reservationIsScopedByOwnerAndKey() {
        paymentIdempotencyService.reserve("cus_ONE", "same-key", "one");
        paymentIdempotencyService.reserve("cus_TWO", "same-key", "two");

        assertTrue(paymentIdempotencyRepository
                .findByOwnerExternalIdAndIdempotencyKey("cus_ONE", "same-key")
                .isPresent());
        assertTrue(paymentIdempotencyRepository
                .findByOwnerExternalIdAndIdempotencyKey("cus_TWO", "same-key")
                .isPresent());
        assertEquals(
                "one",
                paymentIdempotencyRepository
                        .findByOwnerExternalIdAndIdempotencyKey("cus_ONE", "same-key")
                        .orElseThrow()
                        .getRequestFingerprint());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void orderReservationsConvergeDifferentClientKeysOnOneAttempt() throws Exception {
        final String orderId = createOrder("cus_MINE");
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final List<Future<PaymentIdempotencyReservation>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final int attemptNumber = i;
                attempts.add(executor.submit(() -> {
                    start.await();
                    return paymentIdempotencyService.reserveForOrder(
                            "cus_MINE", orderId, "client-key-" + attemptNumber, "same-request");
                }));
            }
            start.countDown();

            int acquired = 0;
            int existingReservations = 0;
            int uniqueLosers = 0;
            for (Future<PaymentIdempotencyReservation> attempt : attempts) {
                try {
                    if (attempt.get().acquired()) {
                        acquired++;
                    } else {
                        existingReservations++;
                    }
                } catch (ExecutionException e) {
                    assertInstanceOf(DataIntegrityViolationException.class, e.getCause());
                    uniqueLosers++;
                }
            }

            assertEquals(1, acquired);
            assertEquals(1, existingReservations + uniqueLosers);
            assertEquals(
                    orderId,
                    paymentIdempotencyRepository
                            .findByOwnerExternalIdAndOrderId("cus_MINE", orderId)
                            .orElseThrow()
                            .getOrderId());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void differentOrdersKeepIndependentReservations() {
        final PaymentIdempotencyReservation first = paymentIdempotencyService.reserveForOrder(
                "owner-independent", createOrder("owner-independent"), "key-first", "first-request");
        final PaymentIdempotencyReservation second = paymentIdempotencyService.reserveForOrder(
                "owner-independent", createOrder("owner-independent"), "key-second", "second-request");

        assertTrue(first.acquired());
        assertTrue(second.acquired());
        assertNotEquals(first.record().getId(), second.record().getId());
    }

    private String createOrder(String owner) {
        return orderRepository
                .saveAndFlush(new CustomerOrder()
                        .setFirstname("Ada")
                        .setLastname("Lovelace")
                        .setEmail(UUID.randomUUID() + "@example.test")
                        .setOrderDate(Instant.now())
                        .setDeliveryAmount(BigDecimal.ZERO)
                        .setTotalAmount(BigDecimal.TEN)
                        .setStatus(OrderStatus.PENDING)
                        .setOwnerExternalId(owner))
                .getId();
    }
}
