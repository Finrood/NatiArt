package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationRequest;
import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentProcessor;
import com.portcelana.natiart.dto.payment.helper.PaymentStatus;

class AsaasPaymentServiceTest {

    private AsaasPaymentService newService() {
        return new AsaasPaymentService("test-api-key", "https://sandbox.asaas.com/api/v3/payments");
    }

    @Test
    void constructor_rejectsBlankApiKey() {
        assertThrows(
                IllegalStateException.class,
                () -> new AsaasPaymentService("  ", "https://sandbox.asaas.com/api/v3/payments"));
        assertThrows(
                IllegalStateException.class,
                () -> new AsaasPaymentService(null, "https://sandbox.asaas.com/api/v3/payments"));
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
    void createPayment_rejectsNonFiniteValue() {
        final AsaasPaymentService service = newService();
        // Stubbed DTOs: the real constructor would throw first, so only a stub
        // proves the service-level guard itself executes.
        final PaymentCreationRequest nanRequest = mock(PaymentCreationRequest.class);
        when(nanRequest.getValue()).thenReturn(Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> service.createPayment(nanRequest, "cus_MINE"));
        final PaymentCreationRequest infiniteRequest = mock(PaymentCreationRequest.class);
        when(infiniteRequest.getValue()).thenReturn(Double.NEGATIVE_INFINITY);
        assertThrows(IllegalArgumentException.class, () -> service.createPayment(infiniteRequest, "cus_MINE"));
    }

    @Test
    void asaasMappingBindsCustomerToRequesterNotRequestBody() {
        final PaymentCreationRequest request =
                new PaymentCreationRequest(PaymentProcessor.ASAAS, "cus_SPOOFED", 10.0, PaymentMethod.PIX);
        final AsaasPaymentCreationRequest mapped = AsaasPaymentCreationRequest.from(request, "cus_MINE");
        assertEquals("cus_MINE", mapped.getCustomer());
        assertEquals(10.0, mapped.getValue());
    }

    @Test
    void parsePaymentMethod_mapsKnownBillingTypes() {
        assertEquals(PaymentMethod.PIX, AsaasPaymentService.parsePaymentMethod("PIX"));
        assertEquals(PaymentMethod.CREDIT_CARD, AsaasPaymentService.parsePaymentMethod("CREDIT_CARD"));
    }

    @Test
    void parsePaymentMethod_rejectsUnknownOrNullBillingType() {
        assertThrows(IllegalArgumentException.class, () -> AsaasPaymentService.parsePaymentMethod("BOLETO"));
        assertThrows(IllegalArgumentException.class, () -> AsaasPaymentService.parsePaymentMethod(null));
        assertThrows(IllegalArgumentException.class, () -> AsaasPaymentService.parsePaymentMethod(""));
    }

    @Test
    void parsePaymentStatus_mapsKnownStatuses() {
        assertEquals(PaymentStatus.PENDING, AsaasPaymentService.parsePaymentStatus("PENDING"));
        assertEquals(PaymentStatus.COMPLETED, AsaasPaymentService.parsePaymentStatus("COMPLETED"));
    }

    @Test
    void parsePaymentStatus_rejectsUnknownOrNullStatus() {
        assertThrows(IllegalArgumentException.class, () -> AsaasPaymentService.parsePaymentStatus("OVERDUE"));
        assertThrows(IllegalArgumentException.class, () -> AsaasPaymentService.parsePaymentStatus(null));
        assertThrows(IllegalArgumentException.class, () -> AsaasPaymentService.parsePaymentStatus(""));
    }

    @Test
    void mapAsaasError_mapsAuthFailuresToUserNotAllowed() {
        assertThrows(UserNotAllowedException.class, () -> {
            throw AsaasPaymentService.mapAsaasError(
                    HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));
        });
        assertThrows(UserNotAllowedException.class, () -> {
            throw AsaasPaymentService.mapAsaasError(
                    HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null));
        });
    }

    @Test
    void mapAsaasError_mapsMissingPaymentToNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> {
            throw AsaasPaymentService.mapAsaasError(
                    HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));
        });
    }

    @Test
    void mapAsaasError_passesThroughUnexpectedUpstreamFailures() {
        final HttpServerErrorException upstream =
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Bad Gateway", null, null, null);
        assertSame(upstream, AsaasPaymentService.mapAsaasError(upstream));
    }

    @Test
    void paymentResourceUrl_buildsEncodedUpstreamUrl() {
        assertEquals(
                "https://sandbox.asaas.com/api/v3/payments/pay_123",
                AsaasPaymentService.paymentResourceUrl("https://sandbox.asaas.com/api/v3/payments", "pay_123"));
        assertEquals(
                "https://sandbox.asaas.com/api/v3/payments/pay_123/pixQrCode",
                AsaasPaymentService.paymentResourceUrl(
                        "https://sandbox.asaas.com/api/v3/payments", "pay_123", "pixQrCode"));
    }

    @Test
    void paymentResourceUrl_rejectsPathManipulatingOrBlankIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AsaasPaymentService.paymentResourceUrl(
                        "https://sandbox.asaas.com/api/v3/payments", "pay_123/secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> AsaasPaymentService.paymentResourceUrl("https://sandbox.asaas.com/api/v3/payments", ".."));
        assertThrows(
                IllegalArgumentException.class,
                () -> AsaasPaymentService.paymentResourceUrl("https://sandbox.asaas.com/api/v3/payments", "  "));
        assertThrows(
                IllegalArgumentException.class,
                () -> AsaasPaymentService.paymentResourceUrl("https://sandbox.asaas.com/api/v3/payments", null));
    }
}
