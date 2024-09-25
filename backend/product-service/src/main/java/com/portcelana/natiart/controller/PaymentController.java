package com.portcelana.natiart.controller;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.PaymentPixQrCodeResponse;
import com.portcelana.natiart.dto.payment.PaymentStatusResponse;
import com.portcelana.natiart.service.PaymentService;
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

    @PostMapping("/api/payment/{paymentId}/status")
    public PaymentStatusResponse getPaymentStatus(@PathVariable String paymentId) {
        return paymentService.getPaymentStatus(paymentId);
    }

    @GetMapping("/api/payment/{paymentId}/pixQrCode")
    public PaymentPixQrCodeResponse getPixQrCode(@PathVariable String paymentId) {
        return paymentService.getPixQrCode(paymentId);
    }
}
