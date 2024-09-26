package com.saas.directory.service;

import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.asaas.AsaasCustomerCreationRequest;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@Service
public class AsaasUserManager {
    private final String asaasCustomerUrl = "https://sandbox.asaas.com/api/v3/customers";

    private RestTemplate restTemplate;

    public AsaasUserManager(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public AsaasCustomerCreationResponse registerUser(UserDto userDto) {
        final HttpHeaders headers = getRequestHeaders();

        final HttpEntity<AsaasCustomerCreationRequest> asaasPaymentCreationRequestHttpEntity = new HttpEntity<>(AsaasCustomerCreationRequest.from(userDto), headers);
        final ResponseEntity<AsaasCustomerCreationResponse> response = restTemplate.postForEntity(asaasCustomerUrl, asaasPaymentCreationRequestHttpEntity, AsaasCustomerCreationResponse.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            final Optional<AsaasCustomerCreationResponse> asaasCustomerCreationResponse = Optional.ofNullable(response.getBody());
            return asaasCustomerCreationResponse
                    .orElseThrow(() -> new IllegalArgumentException("Received a null response body from " + asaasCustomerUrl));
        }
        return null;
    }

    private HttpHeaders getRequestHeaders() {
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", "$aact_YTU5YTE0M2M2N2I4MTliNzk0YTI5N2U5MzdjNWZmNDQ6OjAwMDAwMDAwMDAwMDAwODkyMTA6OiRhYWNoX2Y0YjhkZDZjLTViMTgtNDU1OC1hZDYxLTZiMTUwMzdkM2I5Zg==");

        return headers;
    }
}
