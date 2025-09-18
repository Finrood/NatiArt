package com.saas.directory.service;

import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.asaas.AsaasCustomerCreationRequest;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@Service
public class AsaasUserManager {
    private final String asaasCustomerUrl = "https://sandbox.asaas.com/api/v3/customers";

    private final RestTemplate restTemplate;

    @Value("${natiart.payment.asaas.apikey}")
    private String asaasApiKey;

    public AsaasUserManager(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public AsaasCustomerCreationResponse registerUser(UserDto userDto) throws Exception {
        final HttpHeaders headers = getRequestHeaders();

        final HttpEntity<AsaasCustomerCreationRequest> asaasPaymentCreationRequestHttpEntity = new HttpEntity<>(AsaasCustomerCreationRequest.from(userDto), headers);

        final ResponseEntity<AsaasCustomerCreationResponse> response;
        try {
            response = restTemplate.postForEntity(asaasCustomerUrl, asaasPaymentCreationRequestHttpEntity, AsaasCustomerCreationResponse.class);

            return Optional.ofNullable(response.getBody())
                    .orElseThrow(() -> new RuntimeException("Received a null response body from " + asaasCustomerUrl));
        } catch (HttpClientErrorException e) {
            throw new AsaasApiException(
                String.format("Error from Asaas API: %s", e.getResponseBodyAsString()),
                (HttpStatus) e.getStatusCode()
            );
        } catch (Exception e) {
            throw new Exception("Unexpected error during asaas user registration: " + e);
        }
    }

    private HttpHeaders getRequestHeaders() {
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", asaasApiKey);

        return headers;
    }
}
