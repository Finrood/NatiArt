package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationResponse;
import com.portcelana.natiart.model.Payment;

class AsaasChargeSafetyServiceTest {
    private static final String URL = "https://sandbox.asaas.com/api/v3/payments/pay_1";
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final AsaasChargeSafetyService service =
            new AsaasChargeSafetyService("test-key", "https://sandbox.asaas.com/api/v3/payments", restTemplate);
    private final Payment payment = new Payment("pay_1", "cus_MINE", "order-1");

    @Test
    void verifiedDeletedChargeCanReleaseWithoutAnotherProviderMutation() {
        final AsaasPaymentCreationResponse response = charge("PENDING", true, "cus_MINE");
        when(restTemplate.exchange(
                        eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        assertDoesNotThrow(() -> service.ensureChargeInactive(payment));

        verify(restTemplate, never()).exchange(eq(URL), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void pendingChargeMustBeDeletedBeforeRelease() {
        final AsaasPaymentCreationResponse response = charge("PENDING", false, "cus_MINE");
        when(restTemplate.exchange(
                        eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(response));
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("deleted", true, "id", "pay_1")));

        assertDoesNotThrow(() -> service.ensureChargeInactive(payment));

        verify(restTemplate).exchange(eq(URL), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void receivedChargeIsNeverReleased() {
        final AsaasPaymentCreationResponse response = charge("RECEIVED", false, "cus_MINE");
        when(restTemplate.exchange(
                        eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        assertThrows(IllegalStateException.class, () -> service.ensureChargeInactive(payment));

        verify(restTemplate, never()).exchange(eq(URL), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void foreignChargeIsNeverDeletedOrReleased() {
        final AsaasPaymentCreationResponse response = charge("PENDING", false, "cus_OTHER");
        when(restTemplate.exchange(
                        eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        assertThrows(IllegalStateException.class, () -> service.ensureChargeInactive(payment));

        verify(restTemplate, never()).exchange(eq(URL), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void deleteResponseForAnotherChargeDoesNotAuthorizeRelease() {
        final AsaasPaymentCreationResponse response = charge("PENDING", false, "cus_MINE");
        when(restTemplate.exchange(
                        eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(response));
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("deleted", true, "id", "pay_other")));

        assertThrows(IllegalStateException.class, () -> service.ensureChargeInactive(payment));
    }

    @Test
    void uncertainDeleteLeavesStockReserved() {
        final AsaasPaymentCreationResponse response = charge("PENDING", false, "cus_MINE");
        when(restTemplate.exchange(
                        eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(response));
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThrows(UpstreamServiceException.class, () -> service.ensureChargeInactive(payment));
    }

    private AsaasPaymentCreationResponse charge(String status, boolean deleted, String owner) {
        final AsaasPaymentCreationResponse response = mock(AsaasPaymentCreationResponse.class);
        when(response.getId()).thenReturn("pay_1");
        when(response.getCustomer()).thenReturn(owner);
        when(response.isDeleted()).thenReturn(deleted);
        if (!deleted) {
            when(response.getStatus()).thenReturn(status);
        }
        return response;
    }
}
