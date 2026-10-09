package com.portcelana.natiart.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.dto.payment.*;
import com.portcelana.natiart.service.*;

@RestController
public class AccountPaymentController {
    private final AccountOrderAccessManager access;
    private final PaymentService payments;

    public AccountPaymentController(AccountOrderAccessManager access, PaymentService payments) {
        this.access = access;
        this.payments = payments;
    }

    @PostMapping("/account/payments/create")
    @PreAuthorize("isFullyAuthenticated()")
    public PaymentCreationResponse pay(
            @RequestBody PaymentCreationRequest request,
            @RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return payments.createPayment(request, access.billingOwnerOrDie(principal, request.getOrderId()), key);
    }

    @GetMapping("/account/payments/{id}/status")
    @PreAuthorize("isFullyAuthenticated()")
    public PaymentStatusResponse status(
            @PathVariable String id, @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return payments.getPaymentStatus(id, access.paymentOwnerOrDie(principal, id));
    }

    @GetMapping("/account/payments/{id}/pix-qr-code")
    @PreAuthorize("isFullyAuthenticated()")
    public PaymentPixQrCodeResponse qr(
            @PathVariable String id, @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return payments.getPixQrCode(id, access.paymentOwnerOrDie(principal, id));
    }
}
