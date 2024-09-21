package com.portcelana.natiart.service;

import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationRequest;
import com.portcelana.natiart.dto.payment.asaas.AsaasPaymentCreationResponse;
import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentProcessor;
import com.portcelana.natiart.dto.payment.helper.PaymentStatus;
import org.apache.coyote.BadRequestException;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.MethodNotAllowedException;

import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {
    private final String asaasPaymentUrl = "https://sandbox.asaas.com/api/v3/payments";

    private final RestTemplate restTemplate;

    public PaymentService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public PaymentCreationResponse createPayment(PaymentCreationRequest paymentCreationRequest) {
        if (paymentCreationRequest.getPaymentProcessor() == PaymentProcessor.ASAAS) {
            final HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("access_token", "$aact_YTU5YTE0M2M2N2I4MTliNzk0YTI5N2U5MzdjNWZmNDQ6OjAwMDAwMDAwMDAwMDAwODkyMTA6OiRhYWNoXzc2ZGM5ZTRjLWFlYTMtNGIxNS1hMzk0LWRlZjVhYTE1ODhlMA==");

            final HttpEntity<AsaasPaymentCreationRequest> asaasPaymentCreationRequestHttpEntity = new HttpEntity<>(AsaasPaymentCreationRequest.from(paymentCreationRequest), headers);
            final ResponseEntity<AsaasPaymentCreationResponse> response = restTemplate.postForEntity(asaasPaymentUrl, asaasPaymentCreationRequestHttpEntity, AsaasPaymentCreationResponse.class);

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
            }
            else {
                throw new IllegalArgumentException("Bad request");
            }
        } else {
            throw new IllegalArgumentException(String.format("Unsupported payment processor [%s]", paymentCreationRequest.getPaymentProcessor()));
        }
    }

    public void getPixQrCode(String paymentId) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", "$aact_YTU5YTE0M2M2N2I4MTliNzk0YTI5N2U5MzdjNWZmNDQ6OjAwMDAwMDAwMDAwMDAwODkyMTA6OiRhYWNoXzc2ZGM5ZTRjLWFlYTMtNGIxNS1hMzk0LWRlZjVhYTE1ODhlMA==");
        ResponseEntity<String> forEntity = restTemplate.getForEntity(String.format("{}/{}/pixQrCode", asaasPaymentUrl, paymentId), String.class);
        if (forEntity.getStatusCode() == HttpStatus.OK) {
            System.out.println(forEntity.getBody());
        }
    }
}
