package com.portcelana.natiart.controller;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.PaymentPixQrCodeResponse;
import com.portcelana.natiart.dto.payment.PaymentStatusResponse;
import com.portcelana.natiart.service.PaymentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/payment/create")
    public PaymentCreationResponse createPayment(@RequestBody PaymentCreationRequest paymentCreationRequest) {
        return paymentService.createPayment(paymentCreationRequest);
    }

    @GetMapping("/api/payment/{paymentId}/status")
    @PreAuthorize("isAuthenticated()")
    public PaymentStatusResponse getPaymentStatus(@PathVariable String paymentId,
                                                  @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return paymentService.getPaymentStatus(paymentId, principal != null ? principal.getExternalId() : null);
    }

    @GetMapping("/api/payment/{paymentId}/pixQrCode")
    @PreAuthorize("isAuthenticated()")
    public PaymentPixQrCodeResponse getPixQrCode(@PathVariable String paymentId,
                                                 @AuthenticationPrincipal AuthenticationResponseDto.Principal principal) {
        return paymentService.getPixQrCode(paymentId, principal != null ? principal.getExternalId() : null);
    }
}
