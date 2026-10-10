package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.*;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import(OrderNotificationManager.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderNotificationJpaTest {
    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderNotificationRepository notifications;

    @Autowired
    private OrderNotificationManager manager;

    @Autowired
    private PlatformTransactionManager transactions;

    private CustomerOrder order() {
        return new CustomerOrder()
                .setFirstname("Buyer")
                .setLastname("Example")
                .setEmail("buyer@example.test")
                .setOwnerExternalId("cus_TEST")
                .setOrderDate(Instant.now())
                .setDeliveryAmount(BigDecimal.ZERO)
                .setTotalAmount(new BigDecimal("10.00"))
                .setStatus(OrderStatus.PENDING);
    }

    @Test
    void creationAndTransitionsQueueOnceInTheSameTransactionAndRollbackTogether() {
        final TransactionTemplate tx = new TransactionTemplate(transactions);
        final CustomerOrder order = order();
        tx.executeWithoutResult(ignored -> orders.saveAndFlush(order));
        assertTrue(notifications.existsById(order.getId() + ":PENDING"));
        tx.executeWithoutResult(ignored -> {
            final CustomerOrder current = orders.findById(order.getId()).orElseThrow();
            current.setStatus(OrderStatus.PAID);
            orders.saveAndFlush(current);
            current.setStatus(OrderStatus.PAID);
            orders.saveAndFlush(current);
        });
        assertTrue(notifications.existsById(order.getId() + ":PAID"));
        assertThrows(
                IllegalStateException.class,
                () -> tx.executeWithoutResult(ignored -> {
                    final CustomerOrder current = orders.findById(order.getId()).orElseThrow();
                    current.setStatus(OrderStatus.PROCESSING);
                    orders.saveAndFlush(current);
                    throw new IllegalStateException("rollback");
                }));
        assertFalse(notifications.existsById(order.getId() + ":PROCESSING"));
        assertEquals(
                OrderStatus.PAID, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void deliveryClaimsExcludeAnotherWorkerAndFailureCanBeRetriedWithoutResendingSuccess() {
        final TransactionTemplate tx = new TransactionTemplate(transactions);
        final String id = tx.execute(ignored -> orders.saveAndFlush(order()).getId()) + ":PENDING";
        final OrderMailMessage first = manager.claim(id);
        assertNotNull(first);
        assertNull(manager.claim(id));
        assertThrows(IllegalArgumentException.class, () -> manager.retry(id));
        manager.complete(first, false);
        assertEquals(1, notifications.findById(id).orElseThrow().getAttempts());
        assertNull(manager.claim(id));
        manager.retry(id);
        final OrderMailMessage second = manager.claim(id);
        assertNotNull(second);
        manager.complete(first, true);
        assertNull(notifications.findById(id).orElseThrow().getDeliveredAt());
        manager.complete(second, true);
        assertNotNull(notifications.findById(id).orElseThrow().getDeliveredAt());
        assertNull(manager.claim(id));
        assertThrows(IllegalArgumentException.class, () -> manager.retry(id));
    }

    @Test
    void delayedUnpaidNoticeIsSupersededAfterPaymentInsteadOfDelivered() {
        final TransactionTemplate tx = new TransactionTemplate(transactions);
        final CustomerOrder order = order();
        tx.executeWithoutResult(ignored -> orders.saveAndFlush(order));
        tx.executeWithoutResult(ignored -> {
            final CustomerOrder current = orders.findById(order.getId()).orElseThrow();
            current.setStatus(OrderStatus.PAID);
            orders.saveAndFlush(current);
        });
        assertNull(manager.claim(order.getId() + ":PENDING"));
        assertNotNull(
                notifications.findById(order.getId() + ":PENDING").orElseThrow().getSupersededAt());
        assertThrows(IllegalArgumentException.class, () -> manager.retry(order.getId() + ":PENDING"));
        assertNotNull(manager.claim(order.getId() + ":PAID"));
    }

    @Test
    void failedRetryRetainsSnapshotAndGuestEmailDoesNotExposeIdentityOrCredentials() {
        final TransactionTemplate tx = new TransactionTemplate(transactions);
        final CustomerOrder order = order().setGuestCustomerId("guest-private-id");
        tx.executeWithoutResult(ignored -> orders.saveAndFlush(order));
        final com.portcelana.natiart.model.OrderNotification job =
                notifications.findById(order.getId() + ":PENDING").orElseThrow();
        assertTrue(job.getBody().contains("/en/claim-orders"));
        assertFalse(job.getBody().contains("guest-private-id"));
        assertFalse(job.getBody().contains("cus_TEST"));
        assertFalse(job.getBody().contains("token="));
        assertTrue(job.getBody().contains("Payment is not yet confirmed"));
        assertEquals("OrderMailMessage[redacted]", manager.claim(job.getId()).toString());
    }
}
