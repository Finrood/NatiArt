package com.portcelana.natiart.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.PaymentPixQrCodeResponse;
import com.portcelana.natiart.dto.payment.PaymentStatusResponse;
import com.portcelana.natiart.service.PaymentService;

@RestController
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // Canonical paths use the plural resource name with no /api prefix, matching
    // every sibling controller; the /api/payment variants stay as deprecated
    // aliases so deployed clients keep working.
    @PostMapping({"/payments/create", "/api/payment/create"})
    @PreAuthorize("isFullyAuthenticated()")
    public PaymentCreationResponse createPayment(
            @RequestBody PaymentCreationRequest paymentCreationRequest,
            @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return paymentService.createPayment(
                paymentCreationRequest, principal != null ? principal.getExternalId() : null);
    }

    @GetMapping({"/payments/{paymentId}/status", "/api/payment/{paymentId}/status"})
    @PreAuthorize("isFullyAuthenticated()")
    public PaymentStatusResponse getPaymentStatus(
            @PathVariable String paymentId, @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return paymentService.getPaymentStatus(paymentId, principal != null ? principal.getExternalId() : null);
    }

    // Canonical path is kebab-case per backend/AGENTS.md; legacy variants
    // stay as deprecated aliases so deployed clients keep working.
    @GetMapping({
        "/payments/{paymentId}/pix-qr-code",
        "/api/payment/{paymentId}/pix-qr-code",
        "/api/payment/{paymentId}/pixQrCode"
    })
    @PreAuthorize("isFullyAuthenticated()")
    public PaymentPixQrCodeResponse getPixQrCode(
            @PathVariable String paymentId, @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return paymentService.getPixQrCode(paymentId, principal != null ? principal.getExternalId() : null);
    }
}
