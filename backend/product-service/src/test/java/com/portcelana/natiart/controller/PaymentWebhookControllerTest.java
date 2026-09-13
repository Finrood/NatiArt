package com.portcelana.natiart.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.portcelana.natiart.dto.payment.asaas.AsaasWebhookRequest;
import com.portcelana.natiart.service.PaymentReconciliationService;

class PaymentWebhookControllerTest {
    @Test
    void forgedWebhookIsRejectedBeforeReconciliation() {
        final PaymentReconciliationService service = mock(PaymentReconciliationService.class);
        when(service.hasValidWebhookToken("wrong")).thenReturn(false);
        final PaymentWebhookController controller = new PaymentWebhookController(service);

        final var response = controller.receiveAsaasWebhook("wrong", new AsaasWebhookRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(service, never()).processWebhook(any(AsaasWebhookRequest.class));
    }

    @Test
    void authenticatedWebhookIsAcceptedAfterDurableProcessing() {
        final PaymentReconciliationService service = mock(PaymentReconciliationService.class);
        when(service.hasValidWebhookToken("right")).thenReturn(true);
        final PaymentWebhookController controller = new PaymentWebhookController(service);
        final AsaasWebhookRequest request = new AsaasWebhookRequest();

        final var response = controller.receiveAsaasWebhook("right", request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(service).processWebhook(request);
    }
}
