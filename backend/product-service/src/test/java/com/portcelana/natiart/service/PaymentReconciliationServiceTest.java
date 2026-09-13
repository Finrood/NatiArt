package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;

import com.portcelana.natiart.dto.payment.asaas.AsaasWebhookRequest;
import com.portcelana.natiart.dto.payment.asaas.AsaasWebhookRequest.AsaasWebhookPayment;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.model.PaymentWebhookEvent;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentRepository;
import com.portcelana.natiart.repository.PaymentWebhookEventRepository;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {
    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentWebhookEventRepository webhookEventRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderManager orderManager;

    @Mock
    private AsaasPaymentService asaasPaymentService;

    private PaymentReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new PaymentReconciliationService(
                "webhook-secret", paymentRepository, webhookEventRepository, orderRepository, orderManager, asaasPaymentService);
    }

    @Test
    void webhookTokenUsesConstantTimeComparisonAndRejectsMissingValues() {
        assertTrue(reconciliationService.hasValidWebhookToken("webhook-secret"));
        assertFalse(reconciliationService.hasValidWebhookToken("wrong-secret"));
        assertFalse(reconciliationService.hasValidWebhookToken(null));
        assertFalse(new PaymentReconciliationService(
                        "", paymentRepository, webhookEventRepository, orderRepository, orderManager, asaasPaymentService)
                .hasValidWebhookToken("webhook-secret"));
    }

    @Test
    void receivedWebhookPersistsEventAndMarksPendingOrderPaid() {
        final Payment payment = new Payment("pay-1", "cus-1", "order-1");
        final CustomerOrder order = new CustomerOrder()
                .setOwnerExternalId("cus-1")
                .setTotalAmount(new BigDecimal("25.00"))
                .setStatus(OrderStatus.PENDING);
        when(webhookEventRepository.findByProviderEventId("evt-1")).thenReturn(Optional.empty());
        when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(payment));
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        reconciliationService.processWebhook(webhook("evt-1", "PAYMENT_RECEIVED", "RECEIVED", "25.00"));

        assertEquals("RECEIVED", payment.getProviderStatus());
        verify(paymentRepository).save(payment);
        verify(orderManager).markOrderPaid("order-1");
        verify(webhookEventRepository).saveAndFlush(any(PaymentWebhookEvent.class));
    }

    @Test
    void duplicateWebhookIsIdempotentAndDoesNotReloadOrMutatePayment() {
        when(webhookEventRepository.findByProviderEventId("evt-1"))
                .thenReturn(Optional.of(new PaymentWebhookEvent("evt-1", "PAYMENT_RECEIVED", "pay-1")));

        reconciliationService.processWebhook(webhook("evt-1", "PAYMENT_RECEIVED", "RECEIVED", "25.00"));

        verifyNoInteractions(paymentRepository, orderRepository, orderManager);
        verify(webhookEventRepository, never()).saveAndFlush(any(PaymentWebhookEvent.class));
    }

    @Test
    void amountMismatchIsRejectedBeforeOrderMutationOrEventPersistence() {
        when(webhookEventRepository.findByProviderEventId("evt-1")).thenReturn(Optional.empty());
        when(paymentRepository.findById("pay-1"))
                .thenReturn(Optional.of(new Payment("pay-1", "cus-1", "order-1")));
        when(orderRepository.findById("order-1"))
                .thenReturn(Optional.of(new CustomerOrder()
                        .setOwnerExternalId("cus-1")
                        .setTotalAmount(new BigDecimal("25.00"))
                        .setStatus(OrderStatus.PENDING)));

        assertThrows(
                IllegalArgumentException.class,
                () -> reconciliationService.processWebhook(
                        webhook("evt-1", "PAYMENT_RECEIVED", "RECEIVED", "24.99")));

        verifyNoInteractions(orderManager);
        verify(webhookEventRepository, never()).saveAndFlush(any(PaymentWebhookEvent.class));
    }

    @Test
    void paidWebhookDoesNotReviveCancelledOrder() {
        when(webhookEventRepository.findByProviderEventId("evt-1")).thenReturn(Optional.empty());
        when(paymentRepository.findById("pay-1"))
                .thenReturn(Optional.of(new Payment("pay-1", "cus-1", "order-1")));
        when(orderRepository.findById("order-1"))
                .thenReturn(Optional.of(new CustomerOrder()
                        .setOwnerExternalId("cus-1")
                        .setTotalAmount(new BigDecimal("25.00"))
                        .setStatus(OrderStatus.CANCELLED)));

        reconciliationService.processWebhook(webhook("evt-1", "PAYMENT_CONFIRMED", "CONFIRMED", "25.00"));

        verifyNoInteractions(orderManager);
        verify(webhookEventRepository).saveAndFlush(any(PaymentWebhookEvent.class));
    }

    @Test
    void unsupportedProviderStatusFailsAsUpstreamError() {
        when(webhookEventRepository.findByProviderEventId("evt-1")).thenReturn(Optional.empty());
        when(paymentRepository.findById("pay-1"))
                .thenReturn(Optional.of(new Payment("pay-1", "cus-1", "order-1")));
        when(orderRepository.findById("order-1"))
                .thenReturn(Optional.of(new CustomerOrder()
                        .setOwnerExternalId("cus-1")
                        .setTotalAmount(new BigDecimal("25.00"))
                        .setStatus(OrderStatus.PENDING)));

        final AsaasApiException exception = assertThrows(
                AsaasApiException.class,
                () -> reconciliationService.processWebhook(
                        webhook("evt-1", "PAYMENT_UNKNOWN", "NEW_PROVIDER_STATE", "25.00")));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getHttpStatus());
        verifyNoInteractions(orderManager);
        verify(webhookEventRepository, never()).saveAndFlush(any(PaymentWebhookEvent.class));
    }

    private AsaasWebhookRequest webhook(
            String eventId, String eventType, String status, String value) {
        return new AsaasWebhookRequest()
                .setId(eventId)
                .setEvent(eventType)
                .setPayment(new AsaasWebhookPayment()
                        .setId("pay-1")
                        .setCustomer("cus-1")
                        .setValue(new BigDecimal(value))
                        .setStatus(status)
                        .setCurrency("BRL"));
    }
}
