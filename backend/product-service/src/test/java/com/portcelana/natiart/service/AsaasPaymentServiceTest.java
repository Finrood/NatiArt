package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.PaymentStatusResponse;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationRequest;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationResponse;
import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentProcessor;
import com.portcelana.natiart.dto.payment.helper.PaymentStatus;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.repository.PaymentRepository;

class AsaasPaymentServiceTest {

    private static final String PAYMENTS_URL = "https://sandbox.asaas.com/api/v3/payments";

    private AsaasPaymentService newService() {
        return new AsaasPaymentService(
                "test-api-key", PAYMENTS_URL, mock(RestTemplate.class), mock(PaymentRepository.class));
    }

    private AsaasPaymentService newService(RestTemplate restTemplate, PaymentRepository paymentRepository) {
        return new AsaasPaymentService("test-api-key", PAYMENTS_URL, restTemplate, paymentRepository);
    }

    @Test
    void constructor_rejectsBlankApiKey() {
        assertThrows(
                IllegalStateException.class,
                () -> new AsaasPaymentService("  ", PAYMENTS_URL, mock(PaymentRepository.class)));
        assertThrows(
                IllegalStateException.class,
                () -> new AsaasPaymentService(null, PAYMENTS_URL, mock(PaymentRepository.class)));
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
    void getPaymentStatus_unknownId_404WithoutUpstreamCall() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findById("pay-unknown")).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> newService(restTemplate, paymentRepository).getPaymentStatus("pay-unknown", "cus_MINE"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getPaymentStatus_foreignId_403WithoutUpstreamCall() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(new Payment("pay-1", "cus_OTHER")));

        assertThrows(
                UserNotAllowedException.class,
                () -> newService(restTemplate, paymentRepository).getPaymentStatus("pay-1", "cus_MINE"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getPaymentStatus_ownedId_fetchesUpstreamAfterLocalAuthorization() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(new Payment("pay-1", "cus_MINE")));
        final AsaasPaymentCreationResponse upstream = mock(AsaasPaymentCreationResponse.class);
        when(upstream.getCustomer()).thenReturn("cus_MINE");
        when(upstream.getStatus()).thenReturn("PENDING");
        when(restTemplate.exchange(
                        eq(PAYMENTS_URL + "/pay-1"), eq(HttpMethod.GET), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(upstream));

        final PaymentStatusResponse response =
                newService(restTemplate, paymentRepository).getPaymentStatus("pay-1", "cus_MINE");

        assertEquals("pay-1", response.getPaymentId());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
    }

    @Test
    void getPixQrCode_unknownId_404WithoutUpstreamCall() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findById("pay-unknown")).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> newService(restTemplate, paymentRepository).getPixQrCode("pay-unknown", "cus_MINE"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void createPayment_persistsOwnerMapping() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        final AsaasPaymentCreationResponse upstream = mock(AsaasPaymentCreationResponse.class);
        when(upstream.getId()).thenReturn("pay-9");
        when(upstream.getDateCreated()).thenReturn(LocalDate.of(2026, 9, 6));
        when(upstream.getCustomer()).thenReturn("cus_MINE");
        when(upstream.getBillingType()).thenReturn("PIX");
        when(upstream.getStatus()).thenReturn("PENDING");
        when(upstream.getDueDate()).thenReturn(LocalDate.of(2026, 9, 7));
        when(upstream.getInvoiceUrl()).thenReturn("http://invoice");
        when(upstream.getInvoiceNumber()).thenReturn("001");
        when(restTemplate.postForEntity(eq(PAYMENTS_URL), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(upstream));

        final PaymentCreationResponse response = newService(restTemplate, paymentRepository)
                .createPayment(
                        new PaymentCreationRequest(PaymentProcessor.ASAAS, "cus_MINE", 10.0, PaymentMethod.PIX),
                        "cus_MINE");

        assertEquals("pay-9", response.getPaymentId());
        verify(paymentRepository)
                .save(argThat(
                        payment -> "pay-9".equals(payment.getId()) && "cus_MINE".equals(payment.getOwnerExternalId())));
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
