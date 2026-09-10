package com.portcelana.natiart.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import com.portcelana.natiart.dto.shipping.ShippingEstimate;
import com.portcelana.natiart.dto.shipping.ShippingEstimateRequest;
import com.portcelana.natiart.service.support.MelhorenvioShippingCalculationRequest;
import com.portcelana.natiart.service.support.MelhorenvioShippingCalculationResponse;

@Service
public class ShippingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShippingService.class);

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiToken;
    private final String fromPostalCode;
    private final RetryTemplate retryTemplate;

    public ShippingService(
            @Value("${melhorenvio.api.url}") String apiUrl,
            @Value("${melhorenvio.api.token}") String apiToken,
            @Value("${melhorenvio.api.from-postal-code:88085201}") String fromPostalCode) {
        this(apiUrl, apiToken, fromPostalCode, createRestTemplate());
    }

    ShippingService(String apiUrl, String apiToken, String fromPostalCode, RestTemplate restTemplate) {
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalStateException(
                    "melhorenvio.api.token is blank: set the MELHORENVIO_API_TOKEN environment variable");
        }
        if (fromPostalCode == null || fromPostalCode.isBlank()) {
            throw new IllegalStateException(
                    "melhorenvio.api.from-postal-code is blank: set the MELHORENVIO_FROM_POSTAL_CODE environment variable");
        }
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
        this.fromPostalCode = fromPostalCode;
        this.retryTemplate = createRetryTemplate();
    }

    public List<ShippingEstimate> getShippingEstimates(ShippingEstimateRequest shippingEstimateRequest) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");
        headers.set("Authorization", "Bearer " + apiToken);

        final ResponseEntity<List<MelhorenvioShippingCalculationResponse>> response;
        try {
            response = executeRetryable(() -> restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(createMelhorEnvioRequest(shippingEstimateRequest), headers),
                    new ParameterizedTypeReference<>() {}));
        } catch (HttpStatusCodeException e) {
            throw mapShippingError(e);
        } catch (ResourceAccessException e) {
            throw mapShippingTransportError(e);
        }

        return parseAndFilterResponse(response.getBody());
    }

    private MelhorenvioShippingCalculationRequest createMelhorEnvioRequest(
            ShippingEstimateRequest shippingEstimateRequest) {
        return MelhorenvioShippingCalculationRequest.from(shippingEstimateRequest, fromPostalCode);
    }

    private List<ShippingEstimate> parseAndFilterResponse(List<MelhorenvioShippingCalculationResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return Collections.emptyList();
        }
        return responses.stream()
                .filter(this::isValidResponse)
                .map(this::mapToShippingEstimate)
                .sorted(java.util.Comparator.comparing(ShippingEstimate::getPrice))
                .collect(Collectors.toList());
    }

    private boolean isValidResponse(MelhorenvioShippingCalculationResponse response) {
        return "Correios".equalsIgnoreCase(response.getCompanyName())
                && response.getError() == null
                && response.getPrice() != null;
    }

    private ShippingEstimate mapToShippingEstimate(MelhorenvioShippingCalculationResponse response) {
        return new ShippingEstimate()
                .setService(response.getCompanyName())
                .setPrice(response.getPrice()
                        .add(BigDecimal.valueOf(
                                5))) // We add 5 to compensate for differences between API prices and post office prices
                .setEstimatedDeliveryDays(response.getDelivery_time());
    }

    /**
     * Maps an upstream Melhor Envio HTTP error onto a service exception. The default
     * RestTemplate throws {@link HttpStatusCodeException} instead of returning
     * 4xx/5xx responses, so without this mapping every upstream error would
     * surface as a 500.
     *
     * The raw upstream body is logged server-side only -- it is never embedded
     * in the exception message because the product advice reflects mapped
     * messages to the caller.
     */
    static RuntimeException mapShippingError(HttpStatusCodeException e) {
        LOGGER.warn("Shipping provider API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
        final HttpStatusCode statusCode = e.getStatusCode();
        if (statusCode == HttpStatus.UNAUTHORIZED || statusCode == HttpStatus.FORBIDDEN) {
            return new UserNotAllowedException("Unauthorized api call to the shipping provider");
        }
        if (statusCode == HttpStatus.NOT_FOUND) {
            return new ResourceNotFoundException("Shipping estimate not found in the shipping provider");
        }
        if (statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new UpstreamServiceException(
                    "Shipping provider rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS, retryAfter(e));
        }
        return new UpstreamServiceException("Shipping provider unavailable", HttpStatus.BAD_GATEWAY);
    }

    static UpstreamServiceException mapShippingTransportError(ResourceAccessException e) {
        LOGGER.warn("Shipping provider API transport failure: {}", e.getMessage());
        return new UpstreamServiceException("Shipping provider unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static String retryAfter(HttpStatusCodeException e) {
        final HttpHeaders headers = e.getResponseHeaders();
        if (headers == null) {
            return null;
        }
        final String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static RestTemplate createRestTemplate() {
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return new RestTemplate(factory);
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
}
