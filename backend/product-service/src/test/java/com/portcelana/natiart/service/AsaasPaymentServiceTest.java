package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationRequest;
import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentProcessor;

@ExtendWith(MockitoExtension.class)
class AsaasPaymentServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private AsaasPaymentService newService() {
        return new AsaasPaymentService("test-api-key", "https://sandbox.asaas.com/api/v3/payments");
    }

    @Test
    void ownershipCheckRejectsPaymentOwnedByAnotherCustomer() {
        assertThrows(UserNotAllowedException.class, () -> newService().requireOwnedPayment("cus_OTHER", "cus_MINE"));
    }

    @Test
    void ownershipCheckRejectsAnonymousOrUnknownRequester() {
        final AsaasPaymentService service = newService();
        assertThrows(UserNotAllowedException.class, () -> service.requireOwnedPayment("cus_OWNER", null));
        assertThrows(UserNotAllowedException.class, () -> service.requireOwnedPayment(null, "cus_SOMEONE"));
    }

    @Test
    void ownershipCheckAllowsTheOwner() {
        assertDoesNotThrow(() -> newService().requireOwnedPayment("cus_OWNER", "cus_OWNER"));
    }

    @Test
    void createPaymentRejectsMissingRequester() {
        final AsaasPaymentService service = newService();
        final PaymentCreationRequest request =
                new PaymentCreationRequest(PaymentProcessor.ASAAS, "cus_OTHER", 10.0, PaymentMethod.PIX);
        assertThrows(UserNotAllowedException.class, () -> service.createPayment(request, null));
        assertThrows(UserNotAllowedException.class, () -> service.createPayment(request, "  "));
    }

    @Test
    void createPaymentRejectsNonPositiveValue() {
        final AsaasPaymentService service = newService();
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createPayment(
                        new PaymentCreationRequest(PaymentProcessor.ASAAS, "cus_MINE", 0.0, PaymentMethod.PIX),
                        "cus_MINE"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createPayment(
                        new PaymentCreationRequest(PaymentProcessor.ASAAS, "cus_MINE", -5.0, PaymentMethod.PIX),
                        "cus_MINE"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createPayment(
                        new PaymentCreationRequest(PaymentProcessor.ASAAS, "cus_MINE", null, PaymentMethod.PIX),
                        "cus_MINE"));
    }

    @Test
    void asaasMappingBindsCustomerToRequesterNotRequestBody() {
        final PaymentCreationRequest request =
                new PaymentCreationRequest(PaymentProcessor.ASAAS, "cus_SPOOFED", 10.0, PaymentMethod.PIX);
        final AsaasPaymentCreationRequest mapped = AsaasPaymentCreationRequest.from(request, "cus_MINE");
        assertEquals("cus_MINE", mapped.getCustomer());
        assertEquals(10.0, mapped.getValue());
    }
}
