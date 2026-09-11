package com.portcelana.natiart.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.portcelana.natiart.controller.helper.ResourceAlreadyExistsException;
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
import com.portcelana.natiart.model.PaymentIdempotency;
import com.portcelana.natiart.model.PaymentIdempotencyStatus;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.PaymentRepository;

@Service
public class AsaasPaymentService implements PaymentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsaasPaymentService.class);
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final String asaasPaymentUrl;
    private final RestTemplate restTemplate;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentIdempotencyService paymentIdempotencyService;
    private final RetryTemplate retryTemplate;

    private final String asaasApiKey;

    @Autowired
    public AsaasPaymentService(
            @Value("${natiart.payment.asaas.apikey}") String asaasApiKey,
            @Value("${natiart.payment.asaas.payments-url:https://sandbox.asaas.com/api/v3/payments}")
                    String asaasPaymentUrl,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentIdempotencyService paymentIdempotencyService) {
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
        this.paymentIdempotencyService = paymentIdempotencyService;
        this.retryTemplate = createRetryTemplate();
    }

    AsaasPaymentService(
            String asaasApiKey,
            String asaasPaymentUrl,
            RestTemplate restTemplate,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentIdempotencyService paymentIdempotencyService) {
        this.asaasApiKey = asaasApiKey;
        this.asaasPaymentUrl = asaasPaymentUrl;
        this.restTemplate = restTemplate;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentIdempotencyService = paymentIdempotencyService;
        this.retryTemplate = createRetryTemplate();
    }

    @Override
    public PaymentCreationResponse createPayment(
            PaymentCreationRequest paymentCreationRequest, String requesterExternalId, String idempotencyKey) {
        if (requesterExternalId == null || requesterExternalId.isBlank()) {
            throw new UserNotAllowedException("Authenticated customer is required to create a payment");
        }
        final String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
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
            // Order-linked charges are authorization-checked before anything
            // else: an order owned by another customer must fail closed (403,
            // no upstream egress) even when the quoted value would match.
            requireOwnedOrder(order.getOwnerExternalId(), requesterExternalId);
            if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(value) != 0) {
                throw new IllegalArgumentException(String.format(
                        "Payment value [%s] does not match the total [%s] of order [%s]",
                        value, order.getTotalAmount(), orderId));
            }
        }

        final String requestFingerprint = fingerprint(paymentCreationRequest);
        final PaymentIdempotencyReservation reservationResult =
                reserveOrReload(requesterExternalId, normalizedIdempotencyKey, requestFingerprint);
        final PaymentIdempotency reservation = reservationResult.record();
        if (!Objects.equals(reservation.getRequestFingerprint(), requestFingerprint)) {
            throw new ResourceAlreadyExistsException("Idempotency-Key was already used for a different payment");
        }
        if (reservation.getStatus() == PaymentIdempotencyStatus.SUCCEEDED) {
            return replay(reservation, requesterExternalId, orderId, value);
        }
        if (reservation.getStatus() == PaymentIdempotencyStatus.FAILED_RECOVERABLE) {
            throw new UpstreamServiceException(
                    "Payment request requires reconciliation before retry", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!reservationResult.acquired()) {
            throw new ResourceAlreadyExistsException("Payment creation is already in progress");
        }

        // A legacy order-linked ledger row may predate the reservation table.
        // Adopt it before any provider egress and make future retries durable.
        final Optional<Payment> existing =
                paymentRepository.findByOrderIdAndOwnerExternalId(orderId, requesterExternalId);
        if (existing.isPresent()) {
            paymentIdempotencyService.markSucceeded(
                    requesterExternalId,
                    normalizedIdempotencyKey,
                    existing.get().getId());
            reservation.setProviderPaymentId(existing.get().getId()).setStatus(PaymentIdempotencyStatus.SUCCEEDED);
            return replay(reservation, requesterExternalId, orderId, value);
        }

        final HttpHeaders headers = getRequestHeaders(normalizedIdempotencyKey);

        final HttpEntity<AsaasPaymentCreationRequest> asaasPaymentCreationRequestHttpEntity = new HttpEntity<>(
                AsaasPaymentCreationRequest.from(paymentCreationRequest, requesterExternalId), headers);
        final ResponseEntity<AsaasPaymentCreationResponse> response;
        try {
            response = restTemplate.postForEntity(
                    asaasPaymentUrl, asaasPaymentCreationRequestHttpEntity, AsaasPaymentCreationResponse.class);
        } catch (HttpStatusCodeException e) {
            paymentIdempotencyService.markRecoverableFailure(requesterExternalId, normalizedIdempotencyKey);
            throw mapAsaasError(e);
        } catch (ResourceAccessException e) {
            paymentIdempotencyService.markRecoverableFailure(requesterExternalId, normalizedIdempotencyKey);
            throw mapAsaasTransportError(e);
        } catch (RuntimeException e) {
            paymentIdempotencyService.markRecoverableFailure(requesterExternalId, normalizedIdempotencyKey);
            throw e;
        }

        // The default RestTemplate error handler throws
        // HttpStatusCodeException on any non-2xx (mapped above), so only 2xx
        // bodies reach here: any 2xx (200 today, 201 if Asaas ever follows the
        // creation convention) saves the ledger row first, then responds. A
        // charge without its ledger row is the orphan the save-failure branch
        // below logs for -- and a client retry would then double-charge.
        if (response.getStatusCode().is2xxSuccessful()) {
            final AsaasPaymentCreationResponse responseBody = response.getBody();
            if (responseBody == null) {
                LOGGER.warn("Asaas payment creation response body is null: failing closed");
                paymentIdempotencyService.markRecoverableFailure(requesterExternalId, normalizedIdempotencyKey);
                throw new AsaasApiException("Invalid payment provider response", HttpStatus.BAD_GATEWAY);
            }
            if (responseBody.getId() == null || responseBody.getId().isBlank()) {
                LOGGER.warn("Asaas payment creation response has no payment id: failing closed");
                paymentIdempotencyService.markRecoverableFailure(requesterExternalId, normalizedIdempotencyKey);
                throw new AsaasApiException("Invalid payment provider response", HttpStatus.BAD_GATEWAY);
            }
            try {
                // Charge-then-save is non-atomic by necessity. The durable
                // reservation remains recoverable if this local write fails.
                paymentRepository.save(
                        new Payment(responseBody.getId(), requesterExternalId, orderId, normalizedIdempotencyKey));
                final PaymentCreationResponse paymentResponse = toCreationResponse(responseBody);
                paymentIdempotencyService.markSucceeded(
                        requesterExternalId, normalizedIdempotencyKey, responseBody.getId());
                LOGGER.info(
                        "Payment created: providerPaymentId=[{}], owner=[{}], order=[{}], amount=[{}]",
                        responseBody.getId(),
                        requesterExternalId,
                        orderId,
                        value);
                return paymentResponse;
            } catch (RuntimeException e) {
                LOGGER.warn(
                        "Upstream charge [{}] for owner [{}] (order [{}]) requires reconciliation after local failure",
                        responseBody.getId(),
                        requesterExternalId,
                        orderId);
                paymentIdempotencyService.markRecoverableFailure(requesterExternalId, normalizedIdempotencyKey);
                throw e;
            }
        }
        // Unreachable with the default error handler (non-2xx throws above):
        // fail closed as a provider failure, never as a client error.
        paymentIdempotencyService.markRecoverableFailure(requesterExternalId, normalizedIdempotencyKey);
        throw new AsaasApiException("Invalid payment provider response", HttpStatus.BAD_GATEWAY);
    }

    @Override
    public PaymentCreationResponse createPayment(
            PaymentCreationRequest paymentCreationRequest, String requesterExternalId) {
        return createPayment(paymentCreationRequest, requesterExternalId, null);
    }

    private PaymentIdempotencyReservation reserveOrReload(
            String requesterExternalId, String idempotencyKey, String requestFingerprint) {
        try {
            return paymentIdempotencyService.reserve(requesterExternalId, idempotencyKey, requestFingerprint);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            final PaymentIdempotency record = paymentIdempotencyService
                    .find(requesterExternalId, idempotencyKey)
                    .orElseThrow(() -> e);
            return new PaymentIdempotencyReservation(record, false);
        }
    }

    private PaymentCreationResponse replay(
            PaymentIdempotency reservation, String requesterExternalId, String orderId, BigDecimal value) {
        final String providerPaymentId = reservation.getProviderPaymentId();
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new UpstreamServiceException(
                    "Payment request requires reconciliation before retry", HttpStatus.SERVICE_UNAVAILABLE);
        }
        final PaymentCreationResponse replay = toCreationResponse(fetchPaymentOrDie(providerPaymentId));
        LOGGER.info(
                "Payment replayed: providerPaymentId=[{}], owner=[{}], order=[{}], amount=[{}]",
                providerPaymentId,
                requesterExternalId,
                orderId,
                value);
        return replay;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return UUID.randomUUID().toString();
        }
        final String normalized = idempotencyKey.trim();
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("The Idempotency-Key header is invalid");
        }
        return normalized;
    }

    private String fingerprint(PaymentCreationRequest request) {
        final StringBuilder canonical = new StringBuilder();
        append(canonical, request.getOrderId());
        append(canonical, request.getPaymentProcessor());
        append(canonical, request.getBillingType());
        append(canonical, request.getValue().stripTrailingZeros().toPlainString());
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    private void append(StringBuilder target, Object value) {
        if (value == null) {
            target.append("-1:");
            return;
        }
        final String text = String.valueOf(value);
        target.append(text.length()).append(':').append(text);
    }

    /**
     * Builds the versioned creation response from an upstream payment body.
     * Shared by the fresh-charge path and the idempotent-replay path so a
     * retried POST returns the same shape as the original charge.
     *
     * Upstream date fields are nullable on the wire (absent JSON members
     * deserialize to null): dereferencing them NPEd into a 500, so a missing
     * date fails closed with a static 502 instead.
     */
    private static PaymentCreationResponse toCreationResponse(AsaasPaymentCreationResponse responseBody) {
        if (responseBody.getDateCreated() == null || responseBody.getDueDate() == null) {
            LOGGER.warn("Asaas payment [{}] has null date fields: failing closed", responseBody.getId());
            throw new AsaasApiException("Invalid payment provider response", HttpStatus.BAD_GATEWAY);
        }
        return new PaymentCreationResponse(
                responseBody.getId(),
                responseBody.getDateCreated().atStartOfDay(),
                responseBody.getCustomer(),
                parsePaymentMethod(responseBody.getBillingType()),
                parsePaymentStatus(responseBody.getStatus()),
                responseBody.getDueDate().atStartOfDay(),
                responseBody.getInvoiceUrl(),
                responseBody.getInvoiceNumber());
    }

    public PaymentPixQrCodeResponse getPixQrCode(String paymentId, String requesterExternalId) {
        getPaymentOrDie(paymentId, requesterExternalId);
        requireOwnedPayment(fetchPaymentOrDie(paymentId).getCustomer(), requesterExternalId);

        final HttpEntity<String> entity = new HttpEntity<>(getRequestHeaders());

        final ResponseEntity<AsaasPaymentPixQrCodeResponse> response;
        try {
            response = executeRetryable(() -> restTemplate.exchange(
                    paymentResourceUrl(asaasPaymentUrl, paymentId, "pixQrCode"),
                    HttpMethod.GET,
                    entity,
                    AsaasPaymentPixQrCodeResponse.class));
        } catch (HttpStatusCodeException e) {
            throw mapAsaasError(e);
        } catch (ResourceAccessException e) {
            throw mapAsaasTransportError(e);
        }

        if (response.getStatusCode() == HttpStatus.OK) {
            final Optional<AsaasPaymentPixQrCodeResponse> asaasPaymentPixQrCodeResponse =
                    Optional.ofNullable(response.getBody());
            return asaasPaymentPixQrCodeResponse
                    .map(responseBody -> new PaymentPixQrCodeResponse(
                            responseBody.isSuccess(),
                            responseBody.getEncodedImage(),
                            responseBody.getPayload(),
                            parseExpirationOrDie(responseBody.getExpirationDate())))
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

    /**
     * Parses the upstream PIX expiration timestamp. The wire format is
     * provider-controlled: a null or drifted value fails closed with a static
     * 502 (the raw text is debug-logged server-side only, never echoed).
     */
    private static LocalDateTime parseExpirationOrDie(String expirationDate) {
        if (expirationDate == null) {
            LOGGER.warn("Asaas PIX QR response has a null expiration date: failing closed");
            throw new AsaasApiException("Invalid payment provider response", HttpStatus.BAD_GATEWAY);
        }
        try {
            return LocalDateTime.parse(expirationDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException e) {
            LOGGER.debug("Asaas PIX QR response has an unparseable expiration date [{}]", expirationDate);
            throw new AsaasApiException("Invalid payment provider response", HttpStatus.BAD_GATEWAY);
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
            response = executeRetryable(() -> restTemplate.exchange(
                    paymentResourceUrl(asaasPaymentUrl, paymentId),
                    HttpMethod.GET,
                    new HttpEntity<>(getRequestHeaders()),
                    AsaasPaymentCreationResponse.class));
        } catch (HttpStatusCodeException e) {
            throw mapAsaasError(e);
        } catch (ResourceAccessException e) {
            throw mapAsaasTransportError(e);
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

    private void requireOwnedOrder(String orderOwnerExternalId, String requesterExternalId) {
        if (requesterExternalId == null
                || orderOwnerExternalId == null
                || !orderOwnerExternalId.equals(requesterExternalId)) {
            throw new UserNotAllowedException("The authenticated user does not own this order");
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
        if (statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new UpstreamServiceException(
                    "Payment provider rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS, retryAfter(e));
        }
        return new UpstreamServiceException("Payment provider unavailable", HttpStatus.BAD_GATEWAY);
    }

    static UpstreamServiceException mapAsaasTransportError(ResourceAccessException e) {
        LOGGER.warn("Asaas payment API transport failure: {}", e.getMessage());
        return new UpstreamServiceException("Payment provider unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static String retryAfter(HttpStatusCodeException e) {
        final HttpHeaders headers = e.getResponseHeaders();
        if (headers == null) {
            return null;
        }
        final String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static RetryTemplate createRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(100, 2, 1000)
                .retryOn(HttpServerErrorException.class)
                .retryOn(ResourceAccessException.class)
                .build();
    }

    private <T> T executeRetryable(Supplier<T> request) {
        return retryTemplate.execute(context -> request.get());
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
        return UriComponentsBuilder.fromUriString(baseUrl)
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
        return getRequestHeaders(null);
    }

    private HttpHeaders getRequestHeaders(String idempotencyKey) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", asaasApiKey);
        final String correlationId = MDC.get(com.portcelana.natiart.configuration.RequestCorrelationFilter.MDC_KEY);
        if (correlationId != null) {
            headers.set(com.portcelana.natiart.configuration.RequestCorrelationFilter.HEADER_NAME, correlationId);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }

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
