package com.portcelana.natiart.service;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationResponse;
import com.portcelana.natiart.model.Payment;

/** Stops an unpaid charge at Asaas before its order reservation can be released. */
@Service
public class AsaasChargeSafetyService {
    private final String apiKey;
    private final String paymentsUrl;
    private final RestTemplate restTemplate;

    public AsaasChargeSafetyService(
            @Value("${natiart.payment.asaas.apikey}") String apiKey,
            @Value("${natiart.payment.asaas.payments-url:https://sandbox.asaas.com/api/v3/payments}")
                    String paymentsUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Payment provider API key is required");
        }
        this.apiKey = apiKey;
        this.paymentsUrl = paymentsUrl;
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restTemplate = new RestTemplate(factory);
    }

    AsaasChargeSafetyService(String apiKey, String paymentsUrl, RestTemplate restTemplate) {
        this.apiKey = apiKey;
        this.paymentsUrl = paymentsUrl;
        this.restTemplate = restTemplate;
    }

    public void ensureChargeInactive(Payment payment) {
        final String url = AsaasPaymentService.paymentResourceUrl(paymentsUrl, payment.getId());
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("access_token", apiKey);
        final HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            final ResponseEntity<AsaasPaymentCreationResponse> lookup =
                    restTemplate.exchange(url, HttpMethod.GET, request, AsaasPaymentCreationResponse.class);
            final AsaasPaymentCreationResponse charge = lookup.getBody();
            if (lookup.getStatusCode() != HttpStatus.OK
                    || charge == null
                    || !payment.getId().equals(charge.getId())
                    || !payment.getOwnerExternalId().equals(charge.getCustomer())) {
                throw new IllegalStateException("Payment requires provider reconciliation before stock release");
            }
            if (charge.isDeleted() || "REFUNDED".equals(charge.getStatus())) {
                return;
            }
            if (!"PENDING".equals(charge.getStatus()) && !"OVERDUE".equals(charge.getStatus())) {
                throw new IllegalStateException("Payment requires provider reconciliation before stock release");
            }

            // A successful delete is the serialization point with a payer who may
            // still be completing this charge. A timeout or uncertain response
            // leaves stock reserved for the next reconciliation attempt.
            final ResponseEntity<Map> deletion = restTemplate.exchange(url, HttpMethod.DELETE, request, Map.class);
            final Map<?, ?> body = deletion.getBody();
            if (!deletion.getStatusCode().is2xxSuccessful()
                    || body == null
                    || !Boolean.TRUE.equals(body.get("deleted"))
                    || !payment.getId().equals(body.get("id"))) {
                throw new IllegalStateException("Payment requires provider reconciliation before stock release");
            }
        } catch (RestClientException e) {
            throw new UpstreamServiceException(
                    "Payment requires provider reconciliation before stock release", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
