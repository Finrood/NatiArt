package com.portcelana.natiart.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.*;

import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.payment.*;
import com.portcelana.natiart.service.*;

@RestController
public class GuestTrackingController {
    private final GuestCheckoutClient guests;
    private final GuestTrackingManager tracking;
    private final PaymentService payments;

    public GuestTrackingController(GuestCheckoutClient guests, GuestTrackingManager tracking, PaymentService payments) {
        this.guests = guests;
        this.tracking = tracking;
        this.payments = payments;
    }

    @GetMapping("/guest/tracking/orders")
    public List<OrderDto> history(HttpServletRequest request, @RequestParam(defaultValue = "0") int page) {
        return tracking.history(guests.trackingOrDie(request), page);
    }

    @GetMapping("/guest/tracking/payments/{id}/status")
    public PaymentStatusResponse status(HttpServletRequest request, @PathVariable String id) {
        return payments.getPaymentStatus(id, tracking.paymentOwnerOrDie(guests.trackingOrDie(request), id));
    }

    @GetMapping("/guest/tracking/payments/{id}/pix-qr-code")
    public PaymentPixQrCodeResponse qr(HttpServletRequest request, @PathVariable String id) {
        return payments.getPixQrCode(id, tracking.paymentOwnerOrDie(guests.trackingOrDie(request), id));
    }
}
