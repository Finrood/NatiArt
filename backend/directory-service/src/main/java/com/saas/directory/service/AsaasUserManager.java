package com.saas.directory.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.asaas.AsaasCustomerCreationRequest;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import com.saas.directory.dto.asaas.AsaasCustomerSearchResponse;

@Service
public class AsaasUserManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsaasUserManager.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final String asaasCustomerUrl;
    private final RestTemplate restTemplate;

    private final String asaasApiKey;

    @Autowired
    public AsaasUserManager(
            @Value("${natiart.payment.asaas.apikey}") String asaasApiKey,
            @Value("${natiart.payment.asaas.customers-url:https://sandbox.asaas.com/api/v3/customers}")
                    String asaasCustomerUrl) {
        this(asaasApiKey, asaasCustomerUrl, createRestTemplate());
    }

    AsaasUserManager(String asaasApiKey, String asaasCustomerUrl, RestTemplate restTemplate) {
        if (asaasApiKey == null || asaasApiKey.isBlank()) {
            throw new IllegalStateException(
                    "natiart.payment.asaas.apikey is blank: set the NATIART_PAYMENT_ASAAS_APIKEY environment variable");
        }
        this.asaasApiKey = asaasApiKey;
        this.asaasCustomerUrl = asaasCustomerUrl;
        this.restTemplate = restTemplate;
    }

    public AsaasCustomerCreationResponse registerUser(UserDto userDto) throws Exception {
        final HttpHeaders headers = getRequestHeaders();

        final HttpEntity<AsaasCustomerCreationRequest> asaasPaymentCreationRequestHttpEntity =
                new HttpEntity<>(AsaasCustomerCreationRequest.from(userDto), headers);

        try {
            final AsaasCustomerCreationResponse response = restTemplate.postForObject(
                    asaasCustomerUrl, asaasPaymentCreationRequestHttpEntity, AsaasCustomerCreationResponse.class);

            return Optional.ofNullable(response)
                    .orElseThrow(() -> new RuntimeException("Received a null response body from " + asaasCustomerUrl));
        } catch (HttpClientErrorException e) {
            throw mapAsaasError(e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // Preserve retryable types for the @Retryable listener. Wrapping
            // them in Exception would bypass Spring Retry's classifier.
            throw e;
        } catch (Exception e) {
            throw new Exception("Unexpected error during asaas user registration: " + e.getMessage(), e);
        }
    }

    public List<AsaasCustomerCreationResponse> findCustomersByExternalReference(String externalReference)
            throws Exception {
        if (externalReference == null || externalReference.isBlank()) {
            throw new IllegalArgumentException("External customer reference is required");
        }
        final java.net.URI requestUri = UriComponentsBuilder.fromUriString(asaasCustomerUrl)
                .queryParam("externalReference", externalReference)
                .build()
                .encode()
                .toUri();
        try {
            final ResponseEntity<AsaasCustomerSearchResponse> response = restTemplate.exchange(
                    requestUri, HttpMethod.GET, new HttpEntity<>(getRequestHeaders()), AsaasCustomerSearchResponse.class);
            final AsaasCustomerSearchResponse body = response.getBody();
            if (body == null || body.data() == null) {
                return List.of();
            }
            return body.data().stream()
                    .filter(customer -> externalReference.equals(customer.getExternalReference()))
                    .toList();
        } catch (HttpClientErrorException e) {
            throw mapAsaasError(e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw e;
        } catch (Exception e) {
            throw new Exception("Unexpected error while reconciling Asaas customer", e);
        }
    }

    /**
     * Maps an upstream Asaas customer-API error onto a service exception. The raw
     * upstream body is logged server-side only -- it is never embedded in the
     * exception message because the directory advice reflects that message to
     * the caller.
     */
    static AsaasApiException mapAsaasError(HttpClientErrorException e) {
        LOGGER.warn("Asaas customer API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
        return new AsaasApiException(
                "Customer registration failed at the payment provider", (HttpStatus) e.getStatusCode());
    }

    private static RestTemplate createRestTemplate() {
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return new RestTemplate(factory);
    }

    private HttpHeaders getRequestHeaders() {
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", asaasApiKey);
        final String correlationId = MDC.get(com.saas.directory.configuration.RequestCorrelationFilter.MDC_KEY);
        if (correlationId != null) {
            headers.set(com.saas.directory.configuration.RequestCorrelationFilter.HEADER_NAME, correlationId);
        }

        return headers;
    }
}
