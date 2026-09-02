package com.saas.directory.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.asaas.AsaasCustomerCreationRequest;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;

@Service
public class AsaasUserManager {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final String asaasCustomerUrl;
    private final RestTemplate restTemplate;

    @Value("${natiart.payment.asaas.apikey}")
    private String asaasApiKey;

    public AsaasUserManager(
            @Value("${natiart.payment.asaas.apikey}") String asaasApiKey,
            @Value("${natiart.payment.asaas.customers-url:https://sandbox.asaas.com/api/v3/customers}")
                    String asaasCustomerUrl) {
        this.asaasApiKey = asaasApiKey;
        this.asaasCustomerUrl = asaasCustomerUrl;
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(factory);
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
            throw new AsaasApiException(
                    String.format("Error from Asaas API: %s", e.getResponseBodyAsString()),
                    (HttpStatus) e.getStatusCode());
        } catch (Exception e) {
            throw new Exception("Unexpected error during asaas user registration: " + e.getMessage(), e);
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
