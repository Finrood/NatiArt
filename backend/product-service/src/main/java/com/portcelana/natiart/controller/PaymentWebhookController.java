package com.portcelana.natiart.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.portcelana.natiart.dto.payment.asaas.AsaasWebhookRequest;
import com.portcelana.natiart.service.PaymentReconciliationService;

@RestController
public class PaymentWebhookController {
    private final PaymentReconciliationService reconciliationService;

    public PaymentWebhookController(PaymentReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/webhooks/asaas")
    public ResponseEntity<Void> receiveAsaasWebhook(
            @RequestHeader(value = PaymentReconciliationService.WEBHOOK_TOKEN_HEADER, required = false)
                    String webhookToken,
            @RequestBody AsaasWebhookRequest request) {
        if (!reconciliationService.hasValidWebhookToken(webhookToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        reconciliationService.processWebhook(request);
        return ResponseEntity.accepted().build();
    }
}
