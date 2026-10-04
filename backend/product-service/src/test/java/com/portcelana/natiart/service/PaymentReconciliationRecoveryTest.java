package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationResponse;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentRepository;

@DataJpaTest(properties = {"spring.sql.init.mode=never", "natiart.payment.asaas.webhook-token=fixture-secret"})
@Import({PaymentReconciliationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentReconciliationRecoveryTest {
    @Autowired
    OrderRepository orders;

    @Autowired
    PaymentRepository payments;

    @Autowired
    PaymentReconciliationService reconciliation;

    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactions;

    @Autowired
    jakarta.persistence.EntityManager entityManager;

    @MockitoBean
    AsaasPaymentService provider;

    @MockitoBean
    OrderManager orderManager;

    @BeforeEach
    void clean() {
        payments.deleteAll();
        orders.deleteAll();
        reset(provider, orderManager);
    }

    CustomerOrder order(OrderStatus status, Instant date) {
        return orders.saveAndFlush(new CustomerOrder()
                .setFirstname("Review")
                .setLastname("Probe")
                .setEmail("review@example.test")
                .setOwnerExternalId("cus-review")
                .setOrderDate(date)
                .setDeliveryAmount(BigDecimal.ZERO)
                .setTotalAmount(new BigDecimal("10.00"))
                .setStatus(status));
    }

    AsaasPaymentCreationResponse snapshot(String id, String status) {
        final AsaasPaymentCreationResponse response = mock(AsaasPaymentCreationResponse.class);
        when(response.getId()).thenReturn(id);
        when(response.getCustomer()).thenReturn("cus-review");
        when(response.getValue()).thenReturn(10.0);
        when(response.getStatus()).thenReturn(status);
        when(response.getDateCreated()).thenReturn(LocalDate.of(2026, 9, 30));
        when(response.getDueDate()).thenReturn(LocalDate.of(2026, 10, 1));
        when(response.getBillingType()).thenReturn("PIX");
        return response;
    }

    Payment ledger(String id, CustomerOrder order, String status, Instant createdAt) {
        final Payment payment = new Payment(id, "cus-review", order.getId()).setProviderStatus(status);
        org.springframework.test.util.ReflectionTestUtils.setField(payment, "createdAt", createdAt);
        return payments.saveAndFlush(payment);
    }

    @Test
    void schedulerMustRollbackPaymentStatusWhenOrderTransitionFails() {
        final CustomerOrder order = order(OrderStatus.PENDING, Instant.now());
        ledger("pay-rollback", order, null, Instant.now());
        doReturn(snapshot("pay-rollback", "RECEIVED")).when(provider).fetchPaymentForReconciliation("pay-rollback");
        doThrow(new IllegalStateException("synthetic transient order update failure"))
                .when(orderManager)
                .markOrderPaid(order.getId());
        reconciliation.reconcilePendingPayments();
        assertNull(
                payments.findById("pay-rollback").orElseThrow().getProviderStatus(),
                "payment state and order transition must roll back together");
        reset(orderManager);
        resetDueDates();
        reconciliation.reconcilePendingPayments();
        verify(orderManager).markOrderPaid(order.getId());
    }

    @Test
    void repeatedSweepsMustEventuallyReachPaymentAfterFullPendingPage() {
        for (int i = 0; i < 50; i++) {
            final CustomerOrder old = order(OrderStatus.PENDING, Instant.now().minusSeconds(86400 + i));
            final String id = "old-pay-" + i;
            ledger(id, old, null, Instant.now().minusSeconds(86400 + i));
            doReturn(snapshot(id, "PENDING")).when(provider).fetchPaymentForReconciliation(id);
        }
        final CustomerOrder last = order(OrderStatus.PENDING, Instant.now());
        ledger("pay-after-full-page", last, null, Instant.now());
        doReturn(snapshot("pay-after-full-page", "RECEIVED"))
                .when(provider)
                .fetchPaymentForReconciliation("pay-after-full-page");
        for (int i = 0; i < 3; i++) reconciliation.reconcilePendingPayments();
        verify(orderManager, atLeastOnce()).markOrderPaid(last.getId());
    }

    @Test
    void overduePaymentStillNeedsBackgroundRecovery() {
        final CustomerOrder order = order(OrderStatus.PENDING, Instant.now());
        ledger("pay-overdue", order, "OVERDUE", Instant.now());
        doReturn(snapshot("pay-overdue", "RECEIVED")).when(provider).fetchPaymentForReconciliation("pay-overdue");
        reconciliation.reconcilePendingPayments();
        verify(orderManager).markOrderPaid(order.getId());
    }

    @Test
    void refundCommittedDuringProviderFetchCannotBeOverwrittenByScheduledPaidSnapshot() throws Exception {
        final CustomerOrder order = order(OrderStatus.PENDING, Instant.now());
        ledger("pay-race", order, null, Instant.now());
        final java.util.concurrent.CountDownLatch fetched = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch released = new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation -> {
                    fetched.countDown();
                    assertTrue(released.await(10, java.util.concurrent.TimeUnit.SECONDS));
                    return snapshot("pay-race", "RECEIVED");
                })
                .when(provider)
                .fetchPaymentForReconciliation("pay-race");
        final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            final java.util.concurrent.Future<?> paid = worker.submit(reconciliation::reconcilePendingPayments);
            assertTrue(fetched.await(10, java.util.concurrent.TimeUnit.SECONDS));
            reconciliation.processWebhook(webhook("refund-first", "REFUNDED", "pay-race"));
            released.countDown();
            paid.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("REFUNDED", payments.findById("pay-race").orElseThrow().getProviderStatus());
            verifyNoInteractions(orderManager);
        } finally {
            released.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void competingPaidAndRefundWebhooksSerializeAndKeepTerminalRefund() throws Exception {
        final CustomerOrder order = order(OrderStatus.PENDING, Instant.now());
        ledger("pay-webhook-race", order, null, Instant.now());
        final java.util.concurrent.CountDownLatch paidLocked = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch releasePaid = new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation -> {
                    paidLocked.countDown();
                    assertTrue(releasePaid.await(10, java.util.concurrent.TimeUnit.SECONDS));
                    return null;
                })
                .when(orderManager)
                .markOrderPaid(order.getId());
        final java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            final java.util.concurrent.Future<?> paid = workers.submit(
                    () -> reconciliation.processWebhook(webhook("paid-first", "RECEIVED", "pay-webhook-race")));
            assertTrue(paidLocked.await(10, java.util.concurrent.TimeUnit.SECONDS));
            final java.util.concurrent.Future<?> refund = workers.submit(
                    () -> reconciliation.processWebhook(webhook("refund-second", "REFUNDED", "pay-webhook-race")));
            releasePaid.countDown();
            paid.get(10, java.util.concurrent.TimeUnit.SECONDS);
            refund.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(
                    "REFUNDED",
                    payments.findById("pay-webhook-race").orElseThrow().getProviderStatus());
            reconciliation.processWebhook(webhook("late-paid", "RECEIVED", "pay-webhook-race"));
            verify(orderManager, times(1)).markOrderPaid(order.getId());
        } finally {
            releasePaid.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void authenticatedProviderHttpEventsCommitPaidAndRefundStates() throws Exception {
        final CustomerOrder pending = order(OrderStatus.PENDING, Instant.now());
        ledger("pay-http", pending, "PENDING", Instant.now());
        doAnswer(invocation -> {
                    final CustomerOrder locked =
                            orders.findByIdForUpdate(pending.getId()).orElseThrow();
                    locked.setStatus(OrderStatus.PAID);
                    orders.saveAndFlush(locked);
                    return null;
                })
                .when(orderManager)
                .markOrderPaid(pending.getId());
        final org.springframework.test.web.servlet.MockMvc http =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                                new com.portcelana.natiart.controller.PaymentWebhookController(reconciliation))
                        .build();
        for (final String state : new String[] {"RECEIVED", "REFUNDED"}) {
            final String json = """
                    {"id":"http-%s","event":"PAYMENT_%s","payment":{
                     "id":"pay-http","customer":"cus-review","value":10.00,
                     "status":"%s","billingType":"PIX","currency":"BRL"}}
                    """.formatted(state, state, state);
            http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/webhooks/asaas")
                            .header("asaas-access-token", "fixture-secret")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isAccepted());
            assertEquals(state, payments.findById("pay-http").orElseThrow().getProviderStatus());
            assertEquals(
                    OrderStatus.PAID,
                    orders.findById(pending.getId()).orElseThrow().getStatus());
        }
        verify(orderManager, times(1)).markOrderPaid(pending.getId());
    }

    void resetDueDates() {
        new org.springframework.transaction.support.TransactionTemplate(transactions)
                .executeWithoutResult(status -> entityManager
                        .createQuery("UPDATE Payment p SET p.nextReconciliationAt = NULL")
                        .executeUpdate());
    }

    com.portcelana.natiart.dto.payment.asaas.AsaasWebhookRequest webhook(String event, String status, String id) {
        return new com.portcelana.natiart.dto.payment.asaas.AsaasWebhookRequest()
                .setId(event)
                .setEvent(status.equals("REFUNDED") ? "PAYMENT_REFUNDED" : "PAYMENT_RECEIVED")
                .setPayment(new com.portcelana.natiart.dto.payment.asaas.AsaasWebhookRequest.AsaasWebhookPayment()
                        .setId(id)
                        .setCustomer("cus-review")
                        .setValue(new BigDecimal("10.00"))
                        .setCurrency("BRL")
                        .setStatus(status));
    }
}
