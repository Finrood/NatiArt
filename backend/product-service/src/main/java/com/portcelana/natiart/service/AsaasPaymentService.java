package com.portcelana.natiart.service;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.PaymentPixQrCodeResponse;
import com.portcelana.natiart.dto.payment.PaymentStatusResponse;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationRequest;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationResponse;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentPixQrCodeResponse;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentStatusResponse;
import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class AsaasPaymentService implements PaymentService {
    private final String asaasPaymentUrl = "https://sandbox.asaas.com/api/v3/payments";

    private final RestTemplate restTemplate;

    @Value("${natiart.payment.asaas.apikey}")
    private String asaasApiKey;

    public AsaasPaymentService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public PaymentCreationResponse createPayment(PaymentCreationRequest paymentCreationRequest) {
        final HttpHeaders headers = getRequestHeaders();

        final HttpEntity<AsaasPaymentCreationRequest> asaasPaymentCreationRequestHttpEntity = new HttpEntity<>(AsaasPaymentCreationRequest.from(paymentCreationRequest), headers);
        final ResponseEntity<AsaasPaymentCreationResponse> response = restTemplate.postForEntity(
                asaasPaymentUrl,
                asaasPaymentCreationRequestHttpEntity,
                AsaasPaymentCreationResponse.class
        );

        if (response.getStatusCode() == HttpStatus.OK) {
            final Optional<AsaasPaymentCreationResponse> asaasPaymentCreationResponse = Optional.ofNullable(response.getBody());
            return asaasPaymentCreationResponse
                    .map(responseBody -> new PaymentCreationResponse(
                            responseBody.getId(),
                            responseBody.getDateCreated().atStartOfDay(),
                            responseBody.getCustomer(),
                            PaymentMethod.valueOf(responseBody.getBillingType()),
                            PaymentStatus.valueOf(responseBody.getStatus()),
                            responseBody.getDueDate().atStartOfDay(),
                            responseBody.getInvoiceUrl(),
                            responseBody.getInvoiceNumber()
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("Received a null response body from " + asaasPaymentUrl));
        } else if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            throw new UserNotAllowedException("Unauthorized api call to " + asaasPaymentUrl);
        } else {
            throw new IllegalArgumentException("Bad request");
        }
    }

    public PaymentPixQrCodeResponse getPixQrCode(String paymentId) {
        final HttpEntity<String> entity = new HttpEntity<>(getRequestHeaders());

        final ResponseEntity<AsaasPaymentPixQrCodeResponse> response = restTemplate.exchange(
                String.format("%s/%s/pixQrCode", asaasPaymentUrl, paymentId),
                HttpMethod.GET,
                entity,
                AsaasPaymentPixQrCodeResponse.class
        );

        if (response.getStatusCode() == HttpStatus.OK) {
            final Optional<AsaasPaymentPixQrCodeResponse> asaasPaymentPixQrCodeResponse = Optional.ofNullable(response.getBody());
            return asaasPaymentPixQrCodeResponse
                    .map(responseBody -> new PaymentPixQrCodeResponse(
                            responseBody.isSuccess(),
                            responseBody.getEncodedImage(),
                            responseBody.getPayload(),
                            LocalDateTime.parse(responseBody.getExpirationDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("Received a null response body from " + asaasPaymentUrl));
        } else if (response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN) {
            throw new UserNotAllowedException("Unauthorized api call to " + asaasPaymentUrl);
        } else if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw new ResourceNotFoundException(String.format("Payment with id [%s] not found", paymentId));
        } else {
            throw new IllegalArgumentException("Bad request");
        }
    }

    public PaymentStatusResponse getPaymentStatus(String paymentId) {
        final HttpEntity<String> entity = new HttpEntity<>(getRequestHeaders());

        final ResponseEntity<AsaasPaymentStatusResponse> response = restTemplate.exchange(
                String.format("%s/%s/status", asaasPaymentUrl, paymentId),
                HttpMethod.GET,
                entity,
                AsaasPaymentStatusResponse.class
        );

        if (response.getStatusCode() == HttpStatus.OK) {
            final Optional<AsaasPaymentStatusResponse> asaasPaymentStatusResponse = Optional.ofNullable(response.getBody());
            return asaasPaymentStatusResponse
                    .map(responseBody -> new PaymentStatusResponse(
                            paymentId,
                            PaymentStatus.valueOf(responseBody.getStatus())
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("Received a null response body from " + asaasPaymentUrl));
        } else if (response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN) {
            throw new UserNotAllowedException("Unauthorized api call to " + asaasPaymentUrl);
        } else if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw new ResourceNotFoundException(String.format("Payment with id [%s] not found", paymentId));
        } else {
            throw new IllegalArgumentException("Bad request");
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
