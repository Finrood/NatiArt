package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sun.net.httpserver.HttpServer;

import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentProcessor;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.model.PaymentIdempotencyStatus;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentIdempotencyRepository;
import com.portcelana.natiart.repository.PaymentRepository;

@DataJpaTest(properties = {"spring.sql.init.mode=never", "natiart.test.contract=PaymentHttpCommitContractTest"})
@Import(PaymentIdempotencyService.class)
class PaymentHttpCommitContractTest {
    @Autowired
    private PaymentIdempotencyService idempotency;

    @Autowired
    private PaymentIdempotencyRepository reservations;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private OrderRepository orders;

    private CustomerOrder seed(String owner) {
        return orders.saveAndFlush(new CustomerOrder()
                .setFirstname("Fixture")
                .setLastname("Customer")
                .setEmail("charge@example.test")
                .setOrderDate(Instant.now())
                .setDeliveryAmount(BigDecimal.ZERO)
                .setTotalAmount(new BigDecimal("10.00"))
                .setStatus(OrderStatus.PENDING)
                .setOwnerExternalId(owner));
    }

    private PaymentCreationRequest request(String owner, String order) {
        return new PaymentCreationRequest(
                PaymentProcessor.ASAAS, owner, new BigDecimal("10.00"), PaymentMethod.PIX, order);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void realHttpChargeCommitsLedgerAndReplayUsesGetWithoutASecondPost() throws Exception {
        final String owner = "http-owner-" + UUID.randomUUID();
        final CustomerOrder order = seed(owner);
        try (final Provider provider = new Provider(owner)) {
            final AsaasPaymentService service = new AsaasPaymentService(
                    "inert-fixture-key", provider.url(), payments, orders, idempotency, mock(OrderManager.class));
            final var first = service.createPayment(request(owner, order.getId()), owner, "http-key");
            final var second = service.createPayment(request(owner, order.getId()), owner, "http-key");
            assertEquals(first.getPaymentId(), second.getPaymentId());
            assertEquals(1, provider.posts.get());
            assertEquals(1, provider.gets.get());
            assertEquals("http-key", provider.key.get());
            assertTrue(provider.body.get().contains("\"customer\":\"" + owner + "\""));
            assertTrue(payments.findByOrderIdAndOwnerExternalId(order.getId(), owner)
                    .isPresent());
            assertEquals(
                    PaymentIdempotencyStatus.SUCCEEDED,
                    reservations
                            .findByOwnerExternalIdAndIdempotencyKey(owner, "http-key")
                            .orElseThrow()
                            .getStatus());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void providerChargeFollowedByLocalFailureBlocksAnotherProviderPostAcrossServiceReplacement() throws Exception {
        final String owner = "failure-owner-" + UUID.randomUUID();
        final CustomerOrder order = seed(owner);
        final PaymentRepository failing =
                mock(PaymentRepository.class, org.mockito.AdditionalAnswers.delegatesTo(payments));
        doThrow(new IllegalStateException("fixture ledger failure"))
                .when(failing)
                .save(any(Payment.class));
        try (final Provider provider = new Provider(owner)) {
            final AsaasPaymentService first = new AsaasPaymentService(
                    "inert-fixture-key", provider.url(), failing, orders, idempotency, mock(OrderManager.class));
            assertThrows(
                    IllegalStateException.class,
                    () -> first.createPayment(request(owner, order.getId()), owner, "failure-key"));
            assertEquals(
                    PaymentIdempotencyStatus.FAILED_RECOVERABLE,
                    reservations
                            .findByOwnerExternalIdAndIdempotencyKey(owner, "failure-key")
                            .orElseThrow()
                            .getStatus());
            final AsaasPaymentService restarted = new AsaasPaymentService(
                    "inert-fixture-key", provider.url(), payments, orders, idempotency, mock(OrderManager.class));
            assertThrows(
                    UpstreamServiceException.class,
                    () -> restarted.createPayment(request(owner, order.getId()), owner, "failure-key"));
            assertEquals(1, provider.posts.get());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void malformedSuccessfulChargeResponseLeavesDurableAmbiguityAndCannotBePostedAgain() throws Exception {
        final String owner = "malformed-owner-" + UUID.randomUUID();
        final CustomerOrder order = seed(owner);
        try (final Provider provider = new Provider(owner, true)) {
            final AsaasPaymentService service = new AsaasPaymentService(
                    "inert-fixture-key", provider.url(), payments, orders, idempotency, mock(OrderManager.class));
            assertThrows(
                    org.springframework.web.client.RestClientException.class,
                    () -> service.createPayment(request(owner, order.getId()), owner, "malformed-key"));
            assertEquals(
                    PaymentIdempotencyStatus.FAILED_RECOVERABLE,
                    reservations
                            .findByOwnerExternalIdAndIdempotencyKey(owner, "malformed-key")
                            .orElseThrow()
                            .getStatus());
            assertThrows(
                    UpstreamServiceException.class,
                    () -> service.createPayment(request(owner, order.getId()), owner, "malformed-key"));
            assertEquals(1, provider.posts.get());
        }
    }

    private static final class Provider implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger posts = new AtomicInteger();
        private final AtomicInteger gets = new AtomicInteger();
        private final AtomicReference<String> key = new AtomicReference<>();
        private final AtomicReference<String> body = new AtomicReference<>();

        private Provider(String owner) throws Exception {
            this(owner, false);
        }

        private Provider(String owner, boolean malformed) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            final String id = "pay-" + UUID.randomUUID();
            final String response = malformed
                    ? "{invalid-json"
                    : "{\"id\":\"" + id + "\",\"customer\":\"" + owner
                            + "\",\"value\":10.00,\"billingType\":\"PIX\",\"status\":\"PENDING\",\"dateCreated\":\""
                            + LocalDate.now() + "\",\"dueDate\":\""
                            + LocalDate.now().plusDays(1)
                            + "\",\"discount\":{\"value\":0,\"dueDateLimitDays\":0,\"type\":\"FIXED\"},\"fine\":{\"value\":0,\"type\":\"FIXED\"},\"interest\":{\"value\":0,\"type\":\"PERCENTAGE\"},\"futureProviderField\":\"ignored\"}";
            server.createContext("/payments", exchange -> {
                try (exchange) {
                    if (exchange.getRequestMethod().equals("POST")) {
                        posts.incrementAndGet();
                        key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    } else {
                        gets.incrementAndGet();
                    }
                    final byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
        }

        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/payments";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
