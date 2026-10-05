package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationResponse;
import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentProcessor;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentIdempotencyRepository;
import com.portcelana.natiart.repository.PaymentRepository;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import({PaymentIdempotencyService.class, OrderReservationReaper.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentReplayAndExpiryIntegrationTest {
    @Autowired
    OrderRepository orders;

    @Autowired
    PaymentRepository payments;

    @Autowired
    PaymentIdempotencyRepository attempts;

    @Autowired
    PaymentIdempotencyService idempotency;

    @Autowired
    OrderReservationReaper reaper;

    @MockitoBean
    AsaasPaymentService provider;

    @MockitoBean
    OrderManager orderManager;

    @BeforeEach
    void clean() {
        payments.deleteAll();
        attempts.deleteAll();
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = OrderStatus.class,
            names = {"PAID", "PROCESSING", "SHIPPED"})
    void completedChargeReplayMustSurviveFulfillmentProgress(OrderStatus fulfillmentStatus) {
        final CustomerOrder order = order(OrderStatus.PENDING, Instant.now());
        final RestTemplate http = mock(RestTemplate.class);
        final AsaasPaymentCreationResponse response = snapshot("pay-replay", "PENDING");
        when(http.postForEntity(anyString(), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(response));
        when(http.exchange(anyString(), eq(HttpMethod.GET), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(response));
        final AsaasPaymentService service = new AsaasPaymentService(
                "test-only",
                "https://provider.example.test/payments",
                http,
                payments,
                orders,
                idempotency,
                orderManager);
        final PaymentCreationRequest request = new PaymentCreationRequest(
                PaymentProcessor.ASAAS, "cus-review", new BigDecimal("10.00"), PaymentMethod.PIX, order.getId());
        service.createPayment(request, "cus-review", "review-attempt");
        order.setStatus(fulfillmentStatus);
        orders.saveAndFlush(order);
        final PaymentCreationResponse replay =
                assertDoesNotThrow(() -> service.createPayment(request, "cus-review", "review-attempt"));
        assertEquals("pay-replay", replay.getPaymentId());
        verify(http, times(1)).postForEntity(anyString(), any(), eq(AsaasPaymentCreationResponse.class));
    }

    @Test
    void repeatedExpirySweepsMustReachOrdersAfterUncertainFullPage() {
        for (int i = 0; i < 100; i++) {
            final CustomerOrder old = order(OrderStatus.PENDING, Instant.now().minusSeconds(86400 + i));
            doThrow(new IllegalArgumentException("synthetic unresolved charge"))
                    .when(orderManager)
                    .cancelPendingOrderInternally(old.getId());
        }
        final CustomerOrder later = order(OrderStatus.PENDING, Instant.now().minusSeconds(3600));
        for (int i = 0; i < 3; i++) reaper.expireAbandonedOrders();
        verify(orderManager, atLeastOnce()).cancelPendingOrderInternally(later.getId());
    }
}
