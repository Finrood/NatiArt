package com.portcelana.natiart.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.PaymentPixQrCodeResponse;
import com.portcelana.natiart.dto.payment.PaymentStatusResponse;
import com.portcelana.natiart.dto.payment.asaas.*;
import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentStatus;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentRepository;

@Service
public class AsaasPaymentService implements PaymentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsaasPaymentService.class);

    private final String asaasPaymentUrl;
    private final RestTemplate restTemplate;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    private final String asaasApiKey;

    @Autowired
    public AsaasPaymentService(
            @Value("${natiart.payment.asaas.apikey}") String asaasApiKey,
            @Value("${natiart.payment.asaas.payments-url:https://sandbox.asaas.com/api/v3/payments}")
                    String asaasPaymentUrl,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository) {
        if (asaasApiKey == null || asaasApiKey.isBlank()) {
            throw new IllegalStateException(
                    "natiart.payment.asaas.apikey is blank: set the NATIART_PAYMENT_ASAAS_APIKEY environment variable");
        }
        this.asaasApiKey = asaasApiKey;
        this.asaasPaymentUrl = asaasPaymentUrl;
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restTemplate = new RestTemplate(factory);
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    AsaasPaymentService(
            String asaasApiKey,
            String asaasPaymentUrl,
            RestTemplate restTemplate,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository) {
        this.asaasApiKey = asaasApiKey;
        this.asaasPaymentUrl = asaasPaymentUrl;
        this.restTemplate = restTemplate;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    public PaymentCreationResponse createPayment(
            PaymentCreationRequest paymentCreationRequest, String requesterExternalId) {
        if (requesterExternalId == null || requesterExternalId.isBlank()) {
            throw new UserNotAllowedException("Authenticated customer is required to create a payment");
        }
        // Defense in depth: the DTO constructor already rejects these, but the
        // service must not trust its input shape if that ever changes.
        final BigDecimal value = paymentCreationRequest.getValue();
        if (value == null || value.signum() <= 0 || value.scale() > 2) {
            throw new IllegalArgumentException(
                    "Payment value must be a positive amount with at most two fraction digits");
        }
        final String orderId = paymentCreationRequest.getOrderId();
        if (orderId != null && !orderId.isBlank()) {
            // Client-priced money is never trusted: an order-linked charge must
            // match the server-computed order total exactly, or no upstream
            // charge is created at all.
            final CustomerOrder order = getOrderOrDie(orderId);
            if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(value) != 0) {
                throw new IllegalArgumentException(String.format(
                        "Payment value [%s] does not match the total [%s] of order [%s]",
                        value, order.getTotalAmount(), orderId));
            }
        }
        final HttpHeaders headers = getRequestHeaders();

        final HttpEntity<AsaasPaymentCreationRequest> asaasPaymentCreationRequestHttpEntity = new HttpEntity<>(
                AsaasPaymentCreationRequest.from(paymentCreationRequest, requesterExternalId), headers);
        final ResponseEntity<AsaasPaymentCreationResponse> response;
        try {
            response = restTemplate.postForEntity(
                    asaasPaymentUrl, asaasPaymentCreationRequestHttpEntity, AsaasPaymentCreationResponse.class);
        } catch (HttpStatusCodeException e) {
            throw mapAsaasError(e);
        }

        if (response.getStatusCode() == HttpStatus.OK) {
            final Optional<AsaasPaymentCreationResponse> asaasPaymentCreationResponse =
                    Optional.ofNullable(response.getBody());
            return asaasPaymentCreationResponse
                    .map(responseBody -> {
                        paymentRepository.save(new Payment(responseBody.getId(), requesterExternalId, orderId));
                        return new PaymentCreationResponse(
                                responseBody.getId(),
                                responseBody.getDateCreated().atStartOfDay(),
                                responseBody.getCustomer(),
                                parsePaymentMethod(responseBody.getBillingType()),
                                parsePaymentStatus(responseBody.getStatus()),
                                responseBody.getDueDate().atStartOfDay(),
                                responseBody.getInvoiceUrl(),
                                responseBody.getInvoiceNumber());
                    })
                    .orElseThrow(() ->
                            new IllegalArgumentException("Received a null response body from " + asaasPaymentUrl));
        } else if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            throw new UserNotAllowedException("Unauthorized api call to " + asaasPaymentUrl);
        } else {
            throw new IllegalArgumentException("Bad request");
        }
    }

    public PaymentPixQrCodeResponse getPixQrCode(String paymentId, String requesterExternalId) {
        getPaymentOrDie(paymentId, requesterExternalId);
        requireOwnedPayment(fetchPaymentOrDie(paymentId).getCustomer(), requesterExternalId);

        final HttpEntity<String> entity = new HttpEntity<>(getRequestHeaders());

        final ResponseEntity<AsaasPaymentPixQrCodeResponse> response;
        try {
            response = restTemplate.exchange(
                    paymentResourceUrl(asaasPaymentUrl, paymentId, "pixQrCode"),
                    HttpMethod.GET,
                    entity,
                    AsaasPaymentPixQrCodeResponse.class);
        } catch (HttpStatusCodeException e) {
            throw mapAsaasError(e);
        }

        if (response.getStatusCode() == HttpStatus.OK) {
            final Optional<AsaasPaymentPixQrCodeResponse> asaasPaymentPixQrCodeResponse =
                    Optional.ofNullable(response.getBody());
            return asaasPaymentPixQrCodeResponse
                    .map(responseBody -> new PaymentPixQrCodeResponse(
                            responseBody.isSuccess(),
                            responseBody.getEncodedImage(),
                            responseBody.getPayload(),
                            LocalDateTime.parse(
                                    responseBody.getExpirationDate(),
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                    .orElseThrow(() ->
                            new IllegalArgumentException("Received a null response body from " + asaasPaymentUrl));
        } else if (response.getStatusCode() == HttpStatus.UNAUTHORIZED
                || response.getStatusCode() == HttpStatus.FORBIDDEN) {
            throw new UserNotAllowedException("Unauthorized api call to " + asaasPaymentUrl);
        } else if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw new ResourceNotFoundException(String.format("Payment with id [%s] not found", paymentId));
        } else {
            throw new IllegalArgumentException("Bad request");
        }
    }

    public PaymentStatusResponse getPaymentStatus(String paymentId, String requesterExternalId) {
        getPaymentOrDie(paymentId, requesterExternalId);
        final AsaasPaymentCreationResponse payment = fetchPaymentOrDie(paymentId);
        requireOwnedPayment(payment.getCustomer(), requesterExternalId);

        return new PaymentStatusResponse(
                paymentId, convertAsaasPaymentStatusToGeneralPaymentStatus(parseAsaasStatus(payment.getStatus())));
    }

    private AsaasPaymentCreationResponse fetchPaymentOrDie(String paymentId) {
        final ResponseEntity<AsaasPaymentCreationResponse> response;
        try {
            response = restTemplate.exchange(
                    paymentResourceUrl(asaasPaymentUrl, paymentId),
                    HttpMethod.GET,
                    new HttpEntity<>(getRequestHeaders()),
                    AsaasPaymentCreationResponse.class);
        } catch (HttpStatusCodeException e) {
            throw mapAsaasError(e);
        }
        if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw new ResourceNotFoundException(String.format("Payment with id [%s] not found", paymentId));
        }
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new IllegalArgumentException("Received an invalid response from " + asaasPaymentUrl);
        }
        return response.getBody();
    }

    /**
     * Authorizes a payment read against the locally persisted owner before any
     * upstream egress. Unknown ids fail with 404 and foreign ids with 403
     * without spending an Asaas call on the server key, so probing arbitrary
     * ids reveals nothing about the upstream account and costs nothing.
     */
    Payment getPaymentOrDie(String paymentId, String requesterExternalId) {
        final Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(String.format("Payment with id [%s] not found", paymentId)));
        requireOwnedPayment(payment.getOwnerExternalId(), requesterExternalId);
        return payment;
    }

    private CustomerOrder getOrderOrDie(String orderId) {
        return orderRepository
                .findById(orderId)
                .orElseThrow(
                        () -> new ResourceNotFoundException(String.format("Order with id [%s] not found", orderId)));
    }

    void requireOwnedPayment(String paymentOwnerCustomerId, String requesterExternalId) {
        if (requesterExternalId == null
                || paymentOwnerCustomerId == null
                || !paymentOwnerCustomerId.equals(requesterExternalId)) {
            throw new UserNotAllowedException("The authenticated user does not own this payment");
        }
    }

    private AsaasPaymentStatus parseAsaasStatus(String status) {
        try {
            return AsaasPaymentStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unexpected Asaas status: " + status);
        }
    }

    /**
     * Maps an upstream Asaas HTTP error onto a service exception. The default
     * RestTemplate throws {@link HttpStatusCodeException} instead of returning
     * 4xx/5xx responses, so the status-code branches above would otherwise be
     * dead code and every Asaas 401/404 would surface as a 500.
     *
     * The raw upstream body is logged server-side only -- it is never embedded
     * in the exception message because the product advice reflects mapped
     * messages to the caller.
     */
    static RuntimeException mapAsaasError(HttpStatusCodeException e) {
        LOGGER.warn("Asaas payment API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
        final HttpStatusCode statusCode = e.getStatusCode();
        if (statusCode == HttpStatus.UNAUTHORIZED || statusCode == HttpStatus.FORBIDDEN) {
            return new UserNotAllowedException("Unauthorized api call to the payment provider");
        }
        if (statusCode == HttpStatus.NOT_FOUND) {
            return new ResourceNotFoundException("Payment not found in the payment provider");
        }
        return e;
    }

    /**
     * Builds an upstream Asaas payment URL from a caller-supplied id. The id is
     * allow-listed to a single path segment and encoded -- never interpolated
     * raw, or slashes/{@code ..} would rewrite the upstream path while the
     * {@code access_token} header is attached.
     */
    static String paymentResourceUrl(String baseUrl, String paymentId, String... extraPathSegments) {
        if (paymentId == null || !paymentId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid payment id");
        }
        final String[] segments = new String[extraPathSegments.length + 1];
        segments[0] = paymentId;
        System.arraycopy(extraPathSegments, 0, segments, 1, extraPathSegments.length);
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .pathSegment(segments)
                .encode()
                .toUriString();
    }

    /**
     * Maps the upstream Asaas billing-type string onto our {@link PaymentMethod}.
     * Unknown or null values fail closed with a static message -- the raw upstream
     * text is never echoed into the response body.
     */
    static PaymentMethod parsePaymentMethod(String billingType) {
        try {
            return PaymentMethod.valueOf(billingType);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unexpected Asaas billing type");
        }
    }

    /**
     * Maps the upstream Asaas status string onto our {@link PaymentStatus}.
     * Unknown or null values fail closed with a static message -- the raw upstream
     * text is never echoed into the response body.
     */
    static PaymentStatus parsePaymentStatus(String status) {
        try {
            return PaymentStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unexpected Asaas payment status");
        }
    }

    private HttpHeaders getRequestHeaders() {
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", asaasApiKey);

        return headers;
    }

    private PaymentStatus convertAsaasPaymentStatusToGeneralPaymentStatus(AsaasPaymentStatus asaasPaymentStatus) {
        switch (asaasPaymentStatus) {
            case PENDING -> {
                return PaymentStatus.PENDING;
            }
            case RECEIVED, CONFIRMED -> {
                return PaymentStatus.COMPLETED;
            }
            default -> throw new IllegalArgumentException("Unexpected AsaasPaymentStatus: " + asaasPaymentStatus);
        }
    }
}
