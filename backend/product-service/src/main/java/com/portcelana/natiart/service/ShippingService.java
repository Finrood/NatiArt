package com.portcelana.natiart.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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
    private final String userAgent;
    private final RetryTemplate retryTemplate;
    private List<String> allowedCompanies = List.of("Correios");

    @Value("${melhorenvio.api.allowed-companies:Correios}")
    public void setAllowedCompanies(List<String> companies) {
        final List<String> configured = companies.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (configured.isEmpty()) throw new IllegalArgumentException("At least one shipping carrier must be enabled");
        this.allowedCompanies = configured;
    }

    @Autowired
    public ShippingService(
            @Value("${melhorenvio.api.url}") String apiUrl,
            @Value("${melhorenvio.api.token}") String apiToken,
            @Value("${melhorenvio.api.from-postal-code}") String fromPostalCode,
            @Value("${melhorenvio.api.user-agent}") String userAgent) {
        this(apiUrl, apiToken, fromPostalCode, userAgent, createRestTemplate());
    }

    ShippingService(
            String apiUrl, String apiToken, String fromPostalCode, String userAgent, RestTemplate restTemplate) {
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalStateException(
                    "melhorenvio.api.token is blank: set the MELHORENVIO_API_TOKEN environment variable");
        }
        if (fromPostalCode == null || fromPostalCode.isBlank()) {
            throw new IllegalStateException(
                    "melhorenvio.api.from-postal-code is blank: set the MELHORENVIO_FROM_POSTAL_CODE environment variable");
        }
        if (userAgent == null
                || userAgent.isBlank()
                || userAgent.length() > 255
                || userAgent.chars().anyMatch(Character::isISOControl)
                || !userAgent.matches(".+\\([^\\s()<>@]+@[^\\s()<>@]+\\.[^\\s()<>@]+\\)")) {
            throw new IllegalStateException("Set MELHORENVIO_USER_AGENT to NatiArt (your technical contact email)");
        }
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
        this.fromPostalCode = com.portcelana.natiart.service.support.DomainValidation.cep(fromPostalCode);
        this.userAgent = userAgent;
        this.retryTemplate = createRetryTemplate();
    }

    public List<ShippingEstimate> getShippingEstimates(ShippingEstimateRequest shippingEstimateRequest) {
        return getShippingEstimates(List.of(shippingEstimateRequest));
    }

    /**
     * Calculates rates for the documented packing rule: each product line is
     * expanded into one numeric provider volume per unit using its configured
     * package. Quantity is never encoded as an unsupported parcel field. No client-supplied
     * dimensions are accepted on this order path.
     */
    List<ShippingEstimate> getShippingEstimates(List<ShippingEstimateRequest> shippingEstimateRequests) {
        if (shippingEstimateRequests == null || shippingEstimateRequests.isEmpty()) {
            throw new IllegalArgumentException("At least one shipping volume is required");
        }
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");
        headers.set("Authorization", "Bearer " + apiToken);
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        final String correlationId = MDC.get(com.portcelana.natiart.configuration.RequestCorrelationFilter.MDC_KEY);
        if (correlationId != null) {
            headers.set(com.portcelana.natiart.configuration.RequestCorrelationFilter.HEADER_NAME, correlationId);
        }

        final ResponseEntity<List<MelhorenvioShippingCalculationResponse>> response;
        try {
            response = executeRetryable(() -> restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(createMelhorEnvioRequest(shippingEstimateRequests), headers),
                    new ParameterizedTypeReference<>() {}));
        } catch (HttpStatusCodeException e) {
            throw mapShippingError(e);
        } catch (ResourceAccessException e) {
            throw mapShippingTransportError(e);
        } catch (RestClientException e) {
            LOGGER.warn(
                    "Shipping provider response could not be read: type={}",
                    e.getClass().getSimpleName());
            throw new UpstreamServiceException("Shipping provider unavailable", HttpStatus.BAD_GATEWAY);
        }

        final List<ShippingEstimate> estimates = parseAndFilterResponse(response.getBody());
        LOGGER.info("Shipping estimates calculated: optionCount=[{}]", estimates.size());
        return estimates;
    }

    private MelhorenvioShippingCalculationRequest createMelhorEnvioRequest(
            List<ShippingEstimateRequest> shippingEstimateRequests) {
        return MelhorenvioShippingCalculationRequest.from(shippingEstimateRequests, fromPostalCode);
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
        return response != null
                && response.getId() > 0
                && allowedCompanies.stream().anyMatch(company -> company.equalsIgnoreCase(response.getCompanyName()))
                && response.getError() == null
                && validPrice(effectivePrice(response))
                && (response.getCurrency() == null || "BRL".equals(response.getCurrency()))
                && effectiveDeliveryDays(response) != null
                && effectiveDeliveryDays(response) >= 0;
    }

    private static BigDecimal effectivePrice(MelhorenvioShippingCalculationResponse response) {
        return response.getCustom_price() != null ? response.getCustom_price() : response.getPrice();
    }

    private static Integer effectiveDeliveryDays(MelhorenvioShippingCalculationResponse response) {
        return response.getCustom_delivery_time() != null
                ? response.getCustom_delivery_time()
                : response.getDelivery_time();
    }

    private static boolean validPrice(BigDecimal price) {
        return price != null
                && price.signum() >= 0
                && price.stripTrailingZeros().scale() <= 2
                && price.compareTo(new BigDecimal("99999999.99")) <= 0;
    }

    private ShippingEstimate mapToShippingEstimate(MelhorenvioShippingCalculationResponse response) {
        return new ShippingEstimate()
                .setServiceId(String.valueOf(response.getId()))
                .setService(response.getName() != null ? response.getName() : response.getCompanyName())
                .setPrice(effectivePrice(response).setScale(2))
                .setEstimatedDeliveryDays(effectiveDeliveryDays(response));
    }

    /**
     * Maps an upstream Melhor Envio HTTP error onto a service exception. The default
     * RestTemplate throws {@link HttpStatusCodeException} instead of returning
     * 4xx/5xx responses, so without this mapping every upstream error would
     * surface as a 500.
     *
     * The upstream body is neither logged nor reflected to the caller.
     */
    static RuntimeException mapShippingError(HttpStatusCodeException e) {
        LOGGER.warn(
                "Shipping provider API error: providerStatusCode={}, responseBodyBytes={}",
                e.getStatusCode().value(),
                Math.min(e.getResponseBodyAsByteArray().length, 8192));
        final HttpStatusCode statusCode = e.getStatusCode();
        if (statusCode == HttpStatus.UNAUTHORIZED || statusCode == HttpStatus.FORBIDDEN) {
            return new UpstreamServiceException("Shipping provider unavailable", HttpStatus.BAD_GATEWAY);
        }
        if (statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new UpstreamServiceException(
                    "Shipping provider rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS, retryAfter(e));
        }
        return new UpstreamServiceException("Shipping provider unavailable", HttpStatus.BAD_GATEWAY);
    }

    static UpstreamServiceException mapShippingTransportError(ResourceAccessException e) {
        LOGGER.warn(
                "Shipping provider API transport failure: type={}", e.getClass().getSimpleName());
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
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(8));
        return new RestTemplate(factory);
    }

    private static RetryTemplate createRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(2)
                .exponentialBackoff(100, 2, 1000)
                .retryOn(HttpServerErrorException.class)
                .retryOn(ResourceAccessException.class)
                .build();
    }

    private <T> T executeRetryable(Supplier<T> request) {
        return retryTemplate.execute(context -> request.get());
    }
}
