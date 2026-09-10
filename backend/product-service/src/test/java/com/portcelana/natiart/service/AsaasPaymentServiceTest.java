package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.PaymentStatusResponse;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationRequest;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationResponse;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentPixQrCodeResponse;
import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentProcessor;
import com.portcelana.natiart.dto.payment.helper.PaymentStatus;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentRepository;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class AsaasPaymentServiceTest {

    private static final String PAYMENTS_URL = "https://sandbox.asaas.com/api/v3/payments";

    private AsaasPaymentService newService() {
        return new AsaasPaymentService(
                "test-api-key",
                PAYMENTS_URL,
                mock(RestTemplate.class),
                mock(PaymentRepository.class),
                mock(OrderRepository.class));
    }

    private AsaasPaymentService newService(RestTemplate restTemplate, PaymentRepository paymentRepository) {
        return newService(restTemplate, paymentRepository, mock(OrderRepository.class));
    }

    private AsaasPaymentService newService(
            RestTemplate restTemplate, PaymentRepository paymentRepository, OrderRepository orderRepository) {
        return new AsaasPaymentService("test-api-key", PAYMENTS_URL, restTemplate, paymentRepository, orderRepository);
    }

    @Test
    void constructor_rejectsBlankApiKey() {
        assertThrows(
                IllegalStateException.class,
                () -> new AsaasPaymentService(
                        "  ", PAYMENTS_URL, mock(PaymentRepository.class), mock(OrderRepository.class)));
        assertThrows(
                IllegalStateException.class,
                () -> new AsaasPaymentService(
                        null, PAYMENTS_URL, mock(PaymentRepository.class), mock(OrderRepository.class)));
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
        final PaymentCreationRequest request = new PaymentCreationRequest(
                PaymentProcessor.ASAAS, "cus_OTHER", new BigDecimal("10.00"), PaymentMethod.PIX);
        assertThrows(UserNotAllowedException.class, () -> service.createPayment(request, null));
        assertThrows(UserNotAllowedException.class, () -> service.createPayment(request, "  "));
    }

    @Test
    void createPaymentRejectsNonPositiveValue() {
        final AsaasPaymentService service = newService();
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createPayment(
                        new PaymentCreationRequest(
                                PaymentProcessor.ASAAS, "cus_MINE", BigDecimal.ZERO, PaymentMethod.PIX),
                        "cus_MINE"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createPayment(
                        new PaymentCreationRequest(
                                PaymentProcessor.ASAAS, "cus_MINE", new BigDecimal("-5.00"), PaymentMethod.PIX),
                        "cus_MINE"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createPayment(
                        new PaymentCreationRequest(PaymentProcessor.ASAAS, "cus_MINE", null, PaymentMethod.PIX),
                        "cus_MINE"));
    }

    @Test
    void createPayment_rejectsNullOrOverPreciseValue() {
        final AsaasPaymentService service = newService();
        // Stubbed DTOs: the real constructor would throw first, so only a stub
        // proves the service-level guard itself executes.
        final PaymentCreationRequest nullRequest = mock(PaymentCreationRequest.class);
        when(nullRequest.getValue()).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.createPayment(nullRequest, "cus_MINE"));
        final PaymentCreationRequest overPreciseRequest = mock(PaymentCreationRequest.class);
        when(overPreciseRequest.getValue()).thenReturn(new BigDecimal("10.001"));
        assertThrows(IllegalArgumentException.class, () -> service.createPayment(overPreciseRequest, "cus_MINE"));
    }

    @Test
    void asaasMappingBindsCustomerToRequesterNotRequestBody() {
        final PaymentCreationRequest request = new PaymentCreationRequest(
                PaymentProcessor.ASAAS, "cus_SPOOFED", new BigDecimal("10.00"), PaymentMethod.PIX);
        final AsaasPaymentCreationRequest mapped = AsaasPaymentCreationRequest.from(request, "cus_MINE");
        assertEquals("cus_MINE", mapped.getCustomer());
        assertEquals(new BigDecimal("10.00"), mapped.getValue());
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
    void mapAsaasError_mapsUnexpectedUpstreamFailuresToBadGateway() {
        final HttpServerErrorException upstream =
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Bad Gateway", null, null, null);
        final UpstreamServiceException mapped =
                assertInstanceOf(UpstreamServiceException.class, AsaasPaymentService.mapAsaasError(upstream));
        assertEquals(HttpStatus.BAD_GATEWAY, mapped.getHttpStatus());
        assertEquals("Payment provider unavailable", mapped.getMessage());
    }

    @Test
    void mapAsaasError_mapsRateLimitAndPreservesRetryAfter() {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "7");
        final HttpClientErrorException upstream =
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers, null, null);

        final UpstreamServiceException mapped =
                assertInstanceOf(UpstreamServiceException.class, AsaasPaymentService.mapAsaasError(upstream));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, mapped.getHttpStatus());
        assertEquals("7", mapped.getRetryAfter());
    }

    @Test
    void mapAsaasTransportError_mapsTimeoutToServiceUnavailableWithoutRawMessage() {
        final ResourceAccessException upstream = new ResourceAccessException("connect timed out");

        final UpstreamServiceException mapped = AsaasPaymentService.mapAsaasTransportError(upstream);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, mapped.getHttpStatus());
        assertEquals("Payment provider unavailable", mapped.getMessage());
    }

    @Test
    void mapAsaasError_warnLogsUpstreamStatusAndBodyOnFallThrough() {
        final byte[] body = "{\"errors\":[\"validation-failed-marker\"]}".getBytes(StandardCharsets.UTF_8);
        final HttpClientErrorException upstream = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null, body, StandardCharsets.UTF_8);

        final RuntimeException[] mapped = new RuntimeException[1];
        final List<ILoggingEvent> events = captureLogEvents(
                AsaasPaymentService.class, () -> mapped[0] = AsaasPaymentService.mapAsaasError(upstream));

        assertInstanceOf(UpstreamServiceException.class, mapped[0]);
        assertEquals(1, events.size());
        assertEquals(Level.WARN, events.get(0).getLevel());
        final String message = events.get(0).getFormattedMessage();
        assertTrue(message.contains("400"));
        assertTrue(message.contains("validation-failed-marker"));
    }

    @Test
    void createPayment_doesNotRetryProviderFailure() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final HttpServerErrorException upstream =
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Unavailable", null, null, null);
        when(restTemplate.postForEntity(eq(PAYMENTS_URL), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenThrow(upstream);

        assertThrows(
                UpstreamServiceException.class,
                () -> newService(restTemplate, mock(PaymentRepository.class))
                        .createPayment(
                                new PaymentCreationRequest(
                                        PaymentProcessor.ASAAS, "cus_MINE", new BigDecimal("10.00"), PaymentMethod.PIX),
                                "cus_MINE"));

        verify(restTemplate).postForEntity(eq(PAYMENTS_URL), any(), eq(AsaasPaymentCreationResponse.class));
    }

    @Test
    void mapAsaasError_mappedMessagesStayStaticWithoutUpstreamBody() {
        final byte[] body = "{\"errors\":[\"validation-failed-marker\"]}".getBytes(StandardCharsets.UTF_8);
        final HttpClientErrorException upstream = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", null, body, StandardCharsets.UTF_8);

        final RuntimeException mapped = AsaasPaymentService.mapAsaasError(upstream);

        assertEquals("Unauthorized api call to the payment provider", mapped.getMessage());
        assertFalse(mapped.getMessage().contains("validation-failed-marker"));
    }

    private List<ILoggingEvent> captureLogEvents(Class<?> loggedClass, Runnable action) {
        final Logger logger = (Logger) LoggerFactory.getLogger(loggedClass);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        final Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            action.run();
            return List.copyOf(appender.list);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
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
    void getPaymentStatus_retriesTransientProviderFailureWithBoundedAttempts() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(new Payment("pay-1", "cus_MINE")));
        final HttpServerErrorException upstream =
                HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Unavailable", null, null, null);
        when(restTemplate.exchange(
                        eq(PAYMENTS_URL + "/pay-1"), eq(HttpMethod.GET), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenThrow(upstream);

        assertThrows(
                UpstreamServiceException.class,
                () -> newService(restTemplate, paymentRepository).getPaymentStatus("pay-1", "cus_MINE"));

        verify(restTemplate, times(3))
                .exchange(
                        eq(PAYMENTS_URL + "/pay-1"), eq(HttpMethod.GET), any(), eq(AsaasPaymentCreationResponse.class));
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
                        new PaymentCreationRequest(
                                PaymentProcessor.ASAAS, "cus_MINE", new BigDecimal("10.00"), PaymentMethod.PIX),
                        "cus_MINE");

        assertEquals("pay-9", response.getPaymentId());
        verify(paymentRepository)
                .save(argThat(
                        payment -> "pay-9".equals(payment.getId()) && "cus_MINE".equals(payment.getOwnerExternalId())));
    }

    @Test
    void createPayment_orderLinked_rejectsValueMismatchWithoutUpstreamCharge() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.findById("ord_1"))
                .thenReturn(Optional.of(new CustomerOrder()
                        .setTotalAmount(new BigDecimal("500.00"))
                        .setOwnerExternalId("cus_MINE")));

        // Underpayment: the classic R$0.01-charge-against-a-R$500-order attack.
        final PaymentCreationRequest underpaid = new PaymentCreationRequest(
                PaymentProcessor.ASAAS, "cus_MINE", new BigDecimal("0.01"), PaymentMethod.PIX);
        final IllegalArgumentException underpaymentException = assertThrows(
                IllegalArgumentException.class,
                () -> newService(restTemplate, mock(PaymentRepository.class), orderRepository)
                        .createPayment(orderLinked(underpaid), "cus_MINE"));
        assertTrue(underpaymentException.getMessage().contains("does not match"));
        verifyNoInteractions(restTemplate);

        // Overpayment is equally rejected.
        when(orderRepository.findById("ord_1"))
                .thenReturn(Optional.of(new CustomerOrder()
                        .setTotalAmount(new BigDecimal("10.00"))
                        .setOwnerExternalId("cus_MINE")));
        final PaymentCreationRequest overpaid = new PaymentCreationRequest(
                PaymentProcessor.ASAAS, "cus_MINE", new BigDecimal("10.01"), PaymentMethod.PIX);
        assertThrows(
                IllegalArgumentException.class,
                () -> newService(restTemplate, mock(PaymentRepository.class), orderRepository)
                        .createPayment(orderLinked(overpaid), "cus_MINE"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void createPayment_orderLinked_rejectsUnknownOrder() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.findById("ord_missing")).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> newService(restTemplate, mock(PaymentRepository.class), orderRepository)
                        .createPayment(
                                orderLinked(new PaymentCreationRequest(
                                        PaymentProcessor.ASAAS,
                                        "cus_MINE",
                                        new BigDecimal("10.00"),
                                        PaymentMethod.PIX)),
                                "cus_MINE"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void createPayment_orderLinked_foreignOrder_403WithoutUpstreamEgress() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        final OrderRepository orderRepository = mock(OrderRepository.class);
        // Owner check runs before the value comparison: even an exactly-matching
        // charge against a foreign order must fail closed with zero egress.
        when(orderRepository.findById("ord_1"))
                .thenReturn(Optional.of(new CustomerOrder()
                        .setTotalAmount(new BigDecimal("10.00"))
                        .setOwnerExternalId("cus_OTHER")));

        assertThrows(
                UserNotAllowedException.class,
                () -> newService(restTemplate, paymentRepository, orderRepository)
                        .createPayment(
                                orderLinked(new PaymentCreationRequest(
                                        PaymentProcessor.ASAAS,
                                        "cus_MINE",
                                        new BigDecimal("10.00"),
                                        PaymentMethod.PIX)),
                                "cus_MINE"));
        verifyNoInteractions(restTemplate);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void createPayment_orderLinked_ownerlessOrder_failsClosedWithoutUpstreamEgress() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        final OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.findById("ord_1"))
                .thenReturn(Optional.of(new CustomerOrder().setTotalAmount(new BigDecimal("10.00"))));

        assertThrows(
                UserNotAllowedException.class,
                () -> newService(restTemplate, paymentRepository, orderRepository)
                        .createPayment(
                                orderLinked(new PaymentCreationRequest(
                                        PaymentProcessor.ASAAS,
                                        "cus_MINE",
                                        new BigDecimal("10.00"),
                                        PaymentMethod.PIX)),
                                "cus_MINE"));
        verifyNoInteractions(restTemplate);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void createPayment_orderLinked_exactTotalChargesUpstreamAndPersistsLink() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        final OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.findById("ord_1"))
                .thenReturn(Optional.of(new CustomerOrder()
                        .setTotalAmount(new BigDecimal("10.00"))
                        .setOwnerExternalId("cus_MINE")));
        final AsaasPaymentCreationResponse upstream = mock(AsaasPaymentCreationResponse.class);
        when(upstream.getId()).thenReturn("pay-10");
        when(upstream.getDateCreated()).thenReturn(LocalDate.of(2026, 9, 8));
        when(upstream.getCustomer()).thenReturn("cus_MINE");
        when(upstream.getBillingType()).thenReturn("PIX");
        when(upstream.getStatus()).thenReturn("PENDING");
        when(upstream.getDueDate()).thenReturn(LocalDate.of(2026, 9, 9));
        when(upstream.getInvoiceUrl()).thenReturn("http://invoice");
        when(upstream.getInvoiceNumber()).thenReturn("002");
        when(restTemplate.postForEntity(eq(PAYMENTS_URL), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(upstream));

        final PaymentCreationResponse response = newService(restTemplate, paymentRepository, orderRepository)
                .createPayment(
                        orderLinked(new PaymentCreationRequest(
                                PaymentProcessor.ASAAS, "cus_MINE", new BigDecimal("10.00"), PaymentMethod.PIX)),
                        "cus_MINE");

        assertEquals("pay-10", response.getPaymentId());
        verify(paymentRepository)
                .save(argThat(payment -> "ord_1".equals(payment.getOrderId())
                        && "pay-10".equals(payment.getId())
                        && "cus_MINE".equals(payment.getOwnerExternalId())));
    }

    @Test
    void createPayment_orderLinked_retryReplaysStoredChargeWithoutSecondEgress() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        final OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.findById("ord_1"))
                .thenReturn(Optional.of(new CustomerOrder()
                        .setTotalAmount(new BigDecimal("10.00"))
                        .setOwnerExternalId("cus_MINE")));
        when(paymentRepository.findByOrderIdAndOwnerExternalId("ord_1", "cus_MINE"))
                .thenReturn(Optional.of(new Payment("pay-10", "cus_MINE", "ord_1")));
        final AsaasPaymentCreationResponse upstream = mock(AsaasPaymentCreationResponse.class);
        when(upstream.getId()).thenReturn("pay-10");
        when(upstream.getDateCreated()).thenReturn(LocalDate.of(2026, 9, 8));
        when(upstream.getCustomer()).thenReturn("cus_MINE");
        when(upstream.getBillingType()).thenReturn("PIX");
        when(upstream.getStatus()).thenReturn("PENDING");
        when(upstream.getDueDate()).thenReturn(LocalDate.of(2026, 9, 9));
        when(upstream.getInvoiceUrl()).thenReturn("http://invoice");
        when(upstream.getInvoiceNumber()).thenReturn("002");
        when(restTemplate.exchange(
                        eq(PAYMENTS_URL + "/pay-10"),
                        eq(HttpMethod.GET),
                        any(),
                        eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(upstream));

        final PaymentCreationResponse response = newService(restTemplate, paymentRepository, orderRepository)
                .createPayment(
                        orderLinked(new PaymentCreationRequest(
                                PaymentProcessor.ASAAS, "cus_MINE", new BigDecimal("10.00"), PaymentMethod.PIX)),
                        "cus_MINE");

        assertEquals("pay-10", response.getPaymentId());
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(AsaasPaymentCreationResponse.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPayment_saveFailure_logsOrphanUpstreamIdAndRethrows() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        final OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.findById("ord_1"))
                .thenReturn(Optional.of(new CustomerOrder()
                        .setTotalAmount(new BigDecimal("10.00"))
                        .setOwnerExternalId("cus_MINE")));
        final AsaasPaymentCreationResponse upstream = mock(AsaasPaymentCreationResponse.class);
        when(upstream.getId()).thenReturn("pay-orphan");
        when(upstream.getDateCreated()).thenReturn(LocalDate.of(2026, 9, 8));
        when(upstream.getCustomer()).thenReturn("cus_MINE");
        when(upstream.getBillingType()).thenReturn("PIX");
        when(upstream.getStatus()).thenReturn("PENDING");
        when(upstream.getDueDate()).thenReturn(LocalDate.of(2026, 9, 9));
        when(upstream.getInvoiceUrl()).thenReturn("http://invoice");
        when(upstream.getInvoiceNumber()).thenReturn("002");
        when(restTemplate.postForEntity(eq(PAYMENTS_URL), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(upstream));
        when(paymentRepository.save(any(Payment.class))).thenThrow(new RuntimeException("db down"));

        final AsaasPaymentService service = newService(restTemplate, paymentRepository, orderRepository);
        final PaymentCreationRequest request = orderLinked(new PaymentCreationRequest(
                PaymentProcessor.ASAAS, "cus_MINE", new BigDecimal("10.00"), PaymentMethod.PIX));
        final List<ILoggingEvent> events = captureLogEvents(
                AsaasPaymentService.class,
                () -> assertThrows(RuntimeException.class, () -> service.createPayment(request, "cus_MINE")));

        assertTrue(events.stream()
                .anyMatch(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("pay-orphan")));
    }

    @Test
    void createPayment_nullDateCreated_failsClosedWith502() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        final AsaasPaymentCreationResponse upstream = mock(AsaasPaymentCreationResponse.class);
        when(upstream.getId()).thenReturn("pay-nodate");
        when(upstream.getDateCreated()).thenReturn(null);
        when(upstream.getDueDate()).thenReturn(LocalDate.of(2026, 9, 7));
        when(restTemplate.postForEntity(eq(PAYMENTS_URL), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(upstream));

        final AsaasApiException thrown = assertThrows(
                AsaasApiException.class,
                () -> newService(restTemplate, paymentRepository)
                        .createPayment(
                                new PaymentCreationRequest(
                                        PaymentProcessor.ASAAS, "cus_MINE", new BigDecimal("10.00"), PaymentMethod.PIX),
                                "cus_MINE"));

        assertEquals(HttpStatus.BAD_GATEWAY, thrown.getHttpStatus());
        assertEquals("Invalid payment provider response", thrown.getMessage());
        verify(paymentRepository)
                .save(argThat(payment ->
                        "pay-nodate".equals(payment.getId()) && "cus_MINE".equals(payment.getOwnerExternalId())));
    }

    @Test
    void createPayment_nullDueDate_failsClosedWith502() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        final AsaasPaymentCreationResponse upstream = mock(AsaasPaymentCreationResponse.class);
        when(upstream.getId()).thenReturn("pay-nodue");
        when(upstream.getDateCreated()).thenReturn(LocalDate.of(2026, 9, 6));
        when(upstream.getDueDate()).thenReturn(null);
        when(restTemplate.postForEntity(eq(PAYMENTS_URL), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(upstream));

        final AsaasApiException thrown = assertThrows(
                AsaasApiException.class,
                () -> newService(restTemplate, paymentRepository)
                        .createPayment(
                                new PaymentCreationRequest(
                                        PaymentProcessor.ASAAS, "cus_MINE", new BigDecimal("10.00"), PaymentMethod.PIX),
                                "cus_MINE"));

        assertEquals(HttpStatus.BAD_GATEWAY, thrown.getHttpStatus());
        verify(paymentRepository)
                .save(argThat(payment ->
                        "pay-nodue".equals(payment.getId()) && "cus_MINE".equals(payment.getOwnerExternalId())));
    }

    @Test
    void getPixQrCode_malformedExpirationDate_failsClosedWith502() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(new Payment("pay-1", "cus_MINE")));
        final AsaasPaymentCreationResponse fetched = mock(AsaasPaymentCreationResponse.class);
        when(fetched.getCustomer()).thenReturn("cus_MINE");
        when(restTemplate.exchange(
                        eq(PAYMENTS_URL + "/pay-1"), eq(HttpMethod.GET), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(ResponseEntity.ok(fetched));
        final AsaasPaymentPixQrCodeResponse qr = mock(AsaasPaymentPixQrCodeResponse.class);
        when(qr.getExpirationDate()).thenReturn("23/09/2026 23:59:59");
        when(restTemplate.exchange(
                        eq(PAYMENTS_URL + "/pay-1/pixQrCode"),
                        eq(HttpMethod.GET),
                        any(),
                        eq(AsaasPaymentPixQrCodeResponse.class)))
                .thenReturn(ResponseEntity.ok(qr));

        final AsaasApiException thrown = assertThrows(
                AsaasApiException.class,
                () -> newService(restTemplate, paymentRepository).getPixQrCode("pay-1", "cus_MINE"));

        assertEquals(HttpStatus.BAD_GATEWAY, thrown.getHttpStatus());
        assertEquals("Invalid payment provider response", thrown.getMessage());
    }

    @Test
    void createPayment_non200Success_savesLedgerRowAndResponds() {
        final RestTemplate restTemplate = mock(RestTemplate.class);
        final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        final AsaasPaymentCreationResponse upstream = mock(AsaasPaymentCreationResponse.class);
        when(upstream.getId()).thenReturn("pay-201");
        when(upstream.getDateCreated()).thenReturn(LocalDate.of(2026, 9, 6));
        when(upstream.getCustomer()).thenReturn("cus_MINE");
        when(upstream.getBillingType()).thenReturn("PIX");
        when(upstream.getStatus()).thenReturn("PENDING");
        when(upstream.getDueDate()).thenReturn(LocalDate.of(2026, 9, 7));
        when(upstream.getInvoiceUrl()).thenReturn("http://invoice");
        when(upstream.getInvoiceNumber()).thenReturn("003");
        when(restTemplate.postForEntity(eq(PAYMENTS_URL), any(), eq(AsaasPaymentCreationResponse.class)))
                .thenReturn(new ResponseEntity<>(upstream, HttpStatus.CREATED));

        final PaymentCreationResponse response = newService(restTemplate, paymentRepository)
                .createPayment(
                        new PaymentCreationRequest(
                                PaymentProcessor.ASAAS, "cus_MINE", new BigDecimal("10.00"), PaymentMethod.PIX),
                        "cus_MINE");

        assertEquals("pay-201", response.getPaymentId());
        verify(paymentRepository)
                .save(argThat(payment ->
                        "pay-201".equals(payment.getId()) && "cus_MINE".equals(payment.getOwnerExternalId())));
    }

    private PaymentCreationRequest orderLinked(PaymentCreationRequest request) {
        return new PaymentCreationRequest(
                request.getPaymentProcessor(),
                request.getCustomerId(),
                request.getValue(),
                request.getBillingType(),
                "ord_1");
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
