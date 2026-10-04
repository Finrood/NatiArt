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

    @Test
    void providerHeaderAndJsonAreBoundThroughHttp() throws Exception {
        final PaymentReconciliationService service = mock(PaymentReconciliationService.class);
        when(service.hasValidWebhookToken("right")).thenReturn(true);
        final org.springframework.test.web.servlet.MockMvc http =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                                new PaymentWebhookController(service))
                        .build();
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/webhooks/asaas")
                        .header("asaas-access-token", "right")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(providerJson()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isAccepted());
        final org.mockito.ArgumentCaptor<AsaasWebhookRequest> envelope =
                org.mockito.ArgumentCaptor.forClass(AsaasWebhookRequest.class);
        verify(service).processWebhook(envelope.capture());
        assertEquals("evt-provider", envelope.getValue().getId());
        assertEquals("PAYMENT_RECEIVED", envelope.getValue().getEvent());
        assertEquals("pay-provider", envelope.getValue().getPayment().getId());
        assertEquals(
                new java.math.BigDecimal("10.00"),
                envelope.getValue().getPayment().getValue());
    }

    @Test
    void missingWrongAndObsoleteHeadersCannotProcessProviderEvent() throws Exception {
        final PaymentReconciliationService service = mock(PaymentReconciliationService.class);
        final org.springframework.test.web.servlet.MockMvc http =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                                new PaymentWebhookController(service))
                        .build();
        for (String token : new String[] {"", "wrong"}) {
            final org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request =
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/webhooks/asaas")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(providerJson());
            if (!token.isEmpty()) request.header("asaas-access-token", token);
            http.perform(request)
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                            .isUnauthorized());
        }
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/webhooks/asaas")
                        .header("X-Asaas-Webhook-Token", "right")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(providerJson()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isUnauthorized());
        verify(service, never()).processWebhook(any(AsaasWebhookRequest.class));
    }

    private String providerJson() {
        return """
                {"id":"evt-provider","event":"PAYMENT_RECEIVED","payment":{
                  "id":"pay-provider","customer":"cus-provider","value":10.00,
                  "status":"RECEIVED","billingType":"PIX","currency":"BRL"}}
                """;
    }
}
