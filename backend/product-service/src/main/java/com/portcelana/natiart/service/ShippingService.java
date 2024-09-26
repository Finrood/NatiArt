package com.portcelana.natiart.service;

import com.portcelana.natiart.dto.shipping.ShippingEstimate;
import com.portcelana.natiart.dto.shipping.ShippingEstimateRequest;
import com.portcelana.natiart.service.support.MelhorenvioShippingCalculationRequest;
import com.portcelana.natiart.service.support.MelhorenvioShippingCalculationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ShippingService {
    public static final String FROM_POSTAL_CODE = "88085201";

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiToken;

    public ShippingService(RestTemplateBuilder restTemplateBuilder,
                           @Value("${melhorenvio.api.url}") String apiUrl,
                           @Value("${melhorenvio.api.token}") String apiToken) {
        this.restTemplate = restTemplateBuilder.build();
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
    }

    public List<ShippingEstimate> getShippingEstimates(ShippingEstimateRequest shippingEstimateRequest) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");
        headers.set("Authorization", "Bearer " + apiToken);

        final ResponseEntity<List<MelhorenvioShippingCalculationResponse>> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                new HttpEntity<>(createMelhorEnvioRequest(shippingEstimateRequest), headers),
                new ParameterizedTypeReference<>() {
                }
        );

        return parseAndFilterResponse(response.getBody());
    }

    private MelhorenvioShippingCalculationRequest createMelhorEnvioRequest(ShippingEstimateRequest shippingEstimateRequest) {
        return MelhorenvioShippingCalculationRequest.from(shippingEstimateRequest);
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
                .setPrice(response.getPrice().add(BigDecimal.valueOf(5)))   // We add 5 to compensate for differences between API prices and post office prices
                .setEstimatedDeliveryDays(response.getDelivery_time());
    }
}