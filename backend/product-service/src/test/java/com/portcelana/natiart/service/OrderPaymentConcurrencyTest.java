package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.portcelana.natiart.controller.helper.ResourceAlreadyExistsException;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationResponse;
import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentProcessor;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.PaymentIdempotencyStatus;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentIdempotencyRepository;
import com.portcelana.natiart.repository.PaymentRepository;

/** Exercises two callers against the real unique reservation index before provider egress. */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import(PaymentIdempotencyService.class)
class OrderPaymentConcurrencyTest {
    private static final String PAYMENTS_URL = "https://sandbox.asaas.com/api/v3/payments";

    @Autowired
    private PaymentIdempotencyService idempotencyService;

    @Autowired
    private PaymentIdempotencyRepository idempotencyRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository realOrderRepository;

    @AfterEach
    void cleanCommittedWorkerRows() {
        paymentRepository.deleteAll();
        idempotencyRepository.deleteAll();
        realOrderRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void differentClientKeysForOneOrderCreateOnlyOneProviderCharge() throws Exception {
        race("client-key-one", "client-key-two");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void missingClientKeysForOneOrderCreateOnlyOneProviderCharge() throws Exception {
        race(null, null);
    }

    private void race(String firstKey, String secondKey) throws Exception {
        final CustomerOrder persistedOrder = realOrderRepository.saveAndFlush(new CustomerOrder()
                .setFirstname("Ada")
                .setLastname("Lovelace")
                .setEmail("ada@example.test")
                .setOrderDate(Instant.now())
                .setDeliveryAmount(BigDecimal.ZERO)
                .setTotalAmount(new BigDecimal("10.00"))
                .setStatus(OrderStatus.PENDING)
                .setOwnerExternalId("owner-a"));
        final String orderId = persistedOrder.getId();
        final OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(
                        new CustomerOrder().setOwnerExternalId("owner-a").setTotalAmount(new BigDecimal("10.00"))));
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final AsaasPaymentCreationResponse upstream = mock(AsaasPaymentCreationResponse.class);
        when(upstream.getId()).thenReturn("pay-" + UUID.randomUUID());
        when(upstream.getDateCreated()).thenReturn(LocalDate.of(2026, 9, 27));
        when(upstream.getDueDate()).thenReturn(LocalDate.of(2026, 9, 28));
        when(upstream.getCustomer()).thenReturn("owner-a");
        when(upstream.getBillingType()).thenReturn("PIX");
        when(upstream.getStatus()).thenReturn("PENDING");
        when(restTemplate.postForEntity(eq(PAYMENTS_URL), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(upstream));
        when(restTemplate.exchange(
                        eq(PAYMENTS_URL + "/" + upstream.getId()),
                        eq(HttpMethod.GET),
                        any(),
                        eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(upstream));

        final AsaasPaymentService service = new AsaasPaymentService(
                "test-api-key",
                PAYMENTS_URL,
                restTemplate,
                paymentRepository,
                orderRepository,
                idempotencyService,
                mock(OrderManager.class));
        final PaymentCreationRequest request = new PaymentCreationRequest(
                PaymentProcessor.ASAAS, "owner-a", new BigDecimal("10.00"), PaymentMethod.PIX, orderId);
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final List<Future<PaymentCreationResponse>> calls = List.of(
                    executor.submit(() -> {
                        start.await();
                        return service.createPayment(request, "owner-a", firstKey);
                    }),
                    executor.submit(() -> {
                        start.await();
                        return service.createPayment(request, "owner-a", secondKey);
                    }));
            start.countDown();

            final List<PaymentCreationResponse> completed = new ArrayList<>();
            for (Future<PaymentCreationResponse> call : calls) {
                try {
                    completed.add(call.get(10, TimeUnit.SECONDS));
                } catch (ExecutionException exception) {
                    assertInstanceOf(ResourceAlreadyExistsException.class, exception.getCause());
                }
            }

            assertTrue(completed.size() >= 1);
            assertTrue(completed.stream().allMatch(result -> upstream.getId().equals(result.getPaymentId())));
            verify(restTemplate, times(1))
                    .postForEntity(eq(PAYMENTS_URL), any(), eq(AsaasPaymentCreationResponse.class));
            assertEquals(
                    upstream.getId(),
                    paymentRepository
                            .findByOrderIdAndOwnerExternalId(orderId, "owner-a")
                            .orElseThrow()
                            .getId());
            assertEquals(
                    PaymentIdempotencyStatus.SUCCEEDED,
                    idempotencyRepository
                            .findByOwnerExternalIdAndOrderId("owner-a", orderId)
                            .orElseThrow()
                            .getStatus());
        } finally {
            executor.shutdownNow();
        }
    }
}
