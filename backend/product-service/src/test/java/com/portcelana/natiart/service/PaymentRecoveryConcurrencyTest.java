package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationResponse;
import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentProcessor;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.PaymentIdempotency;
import com.portcelana.natiart.model.PaymentIdempotencyStatus;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentIdempotencyRepository;
import com.portcelana.natiart.repository.PaymentRepository;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import(PaymentIdempotencyService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentRecoveryConcurrencyTest {
    @Autowired
    private PaymentIdempotencyService service;

    @Autowired
    private EntityManager entities;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private PaymentIdempotencyRepository reservations;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private PaymentRepository payments;

    @BeforeEach
    void clean() {
        payments.deleteAll();
        reservations.deleteAll();
        orders.deleteAll();
    }

    @Test
    void successCommittedAfterRecoverySelectionSurvivesAndReplaysWithoutAnotherCharge() throws Exception {
        final CustomerOrder order = orders.saveAndFlush(new CustomerOrder()
                .setFirstname("Race")
                .setLastname("Customer")
                .setEmail("race@example.test")
                .setOwnerExternalId("race-owner")
                .setOrderDate(Instant.now())
                .setStatus(OrderStatus.PENDING)
                .setDeliveryAmount(BigDecimal.ZERO)
                .setTotalAmount(BigDecimal.TEN));
        final CountDownLatch providerEntered = new CountDownLatch(1);
        final CountDownLatch allowProvider = new CountDownLatch(1);
        final CountDownLatch recoveryRead = new CountDownLatch(1);
        final CountDownLatch allowRecoveryWrite = new CountDownLatch(1);
        final RestTemplate http = mock(RestTemplate.class);
        final AsaasPaymentCreationResponse snapshot = mock(AsaasPaymentCreationResponse.class);
        when(snapshot.getId()).thenReturn("pay-confirmed");
        when(snapshot.getCustomer()).thenReturn("race-owner");
        when(snapshot.getValue()).thenReturn(10.0);
        when(snapshot.getStatus()).thenReturn("PENDING");
        when(snapshot.getDateCreated()).thenReturn(LocalDate.of(2026, 10, 5));
        when(snapshot.getDueDate()).thenReturn(LocalDate.of(2026, 10, 6));
        when(snapshot.getBillingType()).thenReturn("PIX");
        when(http.postForEntity(anyString(), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenAnswer(invocation -> {
                    providerEntered.countDown();
                    assertTrue(allowProvider.await(10, TimeUnit.SECONDS));
                    return ResponseEntity.ok(snapshot);
                });
        when(http.exchange(anyString(), eq(HttpMethod.GET), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(snapshot));
        final AsaasPaymentService paymentService = new AsaasPaymentService(
                "test-only",
                "https://provider.example.test/payments",
                http,
                payments,
                orders,
                service,
                mock(OrderManager.class));
        final PaymentCreationRequest request = new PaymentCreationRequest(
                PaymentProcessor.ASAAS, "race-owner", BigDecimal.TEN, PaymentMethod.PIX, order.getId());
        pauseAfterCandidateSelection(recoveryRead, allowRecoveryWrite);
        try (final var workers = Executors.newFixedThreadPool(2)) {
            final var completion =
                    workers.submit(() -> paymentService.createPayment(request, "race-owner", "race-key"));
            try {
                assertTrue(providerEntered.await(10, TimeUnit.SECONDS));
                final PaymentIdempotency record =
                        service.find("race-owner", "race-key").orElseThrow();
                age(record);
                final var recovery = workers.submit(service::recoverStaleReservations);
                try {
                    assertTrue(recoveryRead.await(10, TimeUnit.SECONDS));
                    allowProvider.countDown();
                    assertEquals(
                            "pay-confirmed",
                            completion.get(10, TimeUnit.SECONDS).getPaymentId());
                    assertSuccess(record.getId());
                } finally {
                    allowRecoveryWrite.countDown();
                }
                recovery.get(10, TimeUnit.SECONDS);
                assertSuccess(record.getId());
                // A late failure is another independent transaction, never a demotion of success.
                service.markRecoverableFailure("race-owner", "race-key");
                assertSuccess(record.getId());
                final PaymentCreationResponse replay = paymentService.createPayment(request, "race-owner", "race-key");
                assertEquals("pay-confirmed", replay.getPaymentId());
                verify(http, times(1)).postForEntity(anyString(), any(), eq(AsaasPaymentCreationResponse.class));
            } finally {
                allowProvider.countDown();
                allowRecoveryWrite.countDown();
            }
        }
    }

    @Test
    void refreshedReservationAfterSelectionIsNotFailed() throws Exception {
        final PaymentIdempotency record = reservations.saveAndFlush(new PaymentIdempotency("owner", "key", "fp"));
        age(record);
        final CountDownLatch read = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        pauseAfterCandidateSelection(read, resume);
        try (final var worker = Executors.newSingleThreadExecutor()) {
            final var recovery = worker.submit(service::recoverStaleReservations);
            try {
                assertTrue(read.await(10, TimeUnit.SECONDS));
                jdbc.update(
                        "UPDATE payment_idempotency SET updated_at = ? WHERE id = ?",
                        Timestamp.from(Instant.now()),
                        record.getId());
            } finally {
                resume.countDown();
            }
            recovery.get(10, TimeUnit.SECONDS);
        }
        assertEquals(
                PaymentIdempotencyStatus.IN_PROGRESS,
                reservations.findById(record.getId()).orElseThrow().getStatus());
    }

    @Test
    void recoveryIsBoundedAndSkipsFreshAndTerminalReservations() {
        for (int i = 0; i < 101; i++) {
            age(reservations.saveAndFlush(new PaymentIdempotency("owner", "stale-" + i, "fp")));
        }
        final PaymentIdempotency fresh = reservations.saveAndFlush(new PaymentIdempotency("owner", "fresh", "fp"));
        final PaymentIdempotency succeeded = reservations.saveAndFlush(new PaymentIdempotency("owner", "success", "fp")
                .setStatus(PaymentIdempotencyStatus.SUCCEEDED)
                .setProviderPaymentId("pay-kept"));
        final PaymentIdempotency failed = reservations.saveAndFlush(new PaymentIdempotency("owner", "failed", "fp")
                .setStatus(PaymentIdempotencyStatus.FAILED_RECOVERABLE)
                .setProviderPaymentId("pay-uncertain"));
        age(succeeded);
        age(failed);
        service.recoverStaleReservations();
        assertEquals(
                101,
                reservations.findAll().stream()
                        .filter(r -> r.getStatus() == PaymentIdempotencyStatus.FAILED_RECOVERABLE)
                        .count());
        service.recoverStaleReservations();
        assertEquals(
                102,
                reservations.findAll().stream()
                        .filter(r -> r.getStatus() == PaymentIdempotencyStatus.FAILED_RECOVERABLE)
                        .count());
        assertEquals(
                PaymentIdempotencyStatus.IN_PROGRESS,
                reservations.findById(fresh.getId()).orElseThrow().getStatus());
        assertEquals(
                "pay-kept",
                reservations.findById(succeeded.getId()).orElseThrow().getProviderPaymentId());
        assertEquals(
                PaymentIdempotencyStatus.SUCCEEDED,
                reservations.findById(succeeded.getId()).orElseThrow().getStatus());
        assertEquals(
                "pay-uncertain",
                reservations.findById(failed.getId()).orElseThrow().getProviderPaymentId());
    }

    @Test
    void ordinaryFailureOnlyTransitionsInProgressAndPreservesProviderIdentity() {
        final PaymentIdempotency record = reservations.saveAndFlush(
                new PaymentIdempotency("owner", "key", "fp").setProviderPaymentId("pay-uncertain"));
        service.markRecoverableFailure("owner", "key");
        final PaymentIdempotency failed = reservations.findById(record.getId()).orElseThrow();
        assertEquals(PaymentIdempotencyStatus.FAILED_RECOVERABLE, failed.getStatus());
        assertEquals("pay-uncertain", failed.getProviderPaymentId());
    }

    private void age(PaymentIdempotency record) {
        jdbc.update(
                "UPDATE payment_idempotency SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1800)),
                record.getId());
    }

    private void assertSuccess(String id) {
        final PaymentIdempotency record = reservations.findById(id).orElseThrow();
        assertEquals(PaymentIdempotencyStatus.SUCCEEDED, record.getStatus());
        assertEquals("pay-confirmed", record.getProviderPaymentId());
        assertTrue(payments.findById("pay-confirmed").isPresent());
    }

    private void pauseAfterCandidateSelection(CountDownLatch read, CountDownLatch resume) {
        doAnswer(invocation -> {
                    // Execute the scalar read in recovery's real transaction, then let completion commit.
                    final Pageable page = invocation.getArgument(2);
                    final List<String> ids = entities.createQuery(
                                    "SELECT p.id FROM PaymentIdempotency p WHERE p.status = :status AND p.updatedAt < :cutoff ORDER BY p.updatedAt ASC",
                                    String.class)
                            .setParameter("status", invocation.getArgument(0))
                            .setParameter("cutoff", invocation.getArgument(1))
                            .setMaxResults(page.getPageSize())
                            .getResultList();
                    read.countDown();
                    assertTrue(resume.await(10, TimeUnit.SECONDS));
                    return ids;
                })
                .when(reservations)
                .findStaleIdsByStatus(any(), any(), any());
    }
}
