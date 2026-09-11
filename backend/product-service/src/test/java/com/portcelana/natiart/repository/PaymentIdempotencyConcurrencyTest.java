package com.portcelana.natiart.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

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

        assertEquals(2, paymentIdempotencyRepository.count());
        assertEquals(
                "one",
                paymentIdempotencyRepository
                        .findByOwnerExternalIdAndIdempotencyKey("cus_ONE", "same-key")
                        .orElseThrow()
                        .getRequestFingerprint());
    }
}
