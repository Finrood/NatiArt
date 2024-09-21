package com.portcelana.natiart.controller;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
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

    @GetMapping("/api/payment/{paymentId}/pixQrCode")
    public void getPixQrCode(@PathVariable String paymentId) {
        paymentService.getPixQrCode(paymentId);
    }
}
