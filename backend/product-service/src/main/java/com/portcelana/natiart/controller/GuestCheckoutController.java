package com.portcelana.natiart.controller;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.portcelana.natiart.dto.*;
import com.portcelana.natiart.dto.payment.*;
import com.portcelana.natiart.dto.shipping.*;
import com.portcelana.natiart.service.*;

@RestController
public class GuestCheckoutController {
    private final GuestCheckoutClient guests;
    private final GuestOrderManager access;
    private final OrderManager orders;
    private final OrderViewService views;
    private final ShippingQuoteService shipping;
    private final CustomerUploadService uploads;
    private final PaymentService payments;

    public GuestCheckoutController(
            GuestCheckoutClient guests,
            GuestOrderManager access,
            OrderManager orders,
            OrderViewService views,
            ShippingQuoteService shipping,
            CustomerUploadService uploads,
            PaymentService payments) {
        this.guests = guests;
        this.access = access;
        this.orders = orders;
        this.views = views;
        this.shipping = shipping;
        this.uploads = uploads;
        this.payments = payments;
    }

    @PostMapping("/guest/shipping/quote")
    public ShippingQuoteResponse quote(HttpServletRequest request, @RequestBody ShippingQuoteRequest quote) {
        return shipping.createQuote(quote, guests.validateOrDie(request, true).externalId());
    }

    @PostMapping(value = "/guest/customer/uploads", consumes = "multipart/form-data")
    public CustomerUploadResponse upload(HttpServletRequest request, @RequestPart("file") MultipartFile file)
            throws IOException {
        return uploads.upload(guests.validateOrDie(request, true).externalId(), file);
    }

    @PostMapping("/guest/orders/create")
    public OrderDto create(
            HttpServletRequest request, @RequestBody OrderDto order, @RequestHeader("Idempotency-Key") String key) {
        final GuestCheckoutDto guest = guests.validateOrDie(request, true);
        order.setEmail(guest.email());
        final com.portcelana.natiart.model.CustomerOrder created =
                orders.createGuestOrder(order, guest.externalId(), guest.customerId(), key);
        access.orderOwnerOrDie(guest, created.getId());
        return views.getCustomerOrder(created.getId(), guest.externalId());
    }

    @GetMapping("/guest/orders/{id}")
    public OrderDto order(HttpServletRequest request, @PathVariable String id) {
        final GuestCheckoutDto guest = guests.validateOrDie(request, false);
        return views.getCustomerOrder(id, access.orderOwnerOrDie(guest, id));
    }

    @DeleteMapping("/guest/orders/{id}")
    public OrderDto cancel(HttpServletRequest request, @PathVariable String id) {
        final GuestCheckoutDto guest = guests.validateOrDie(request, true);
        return orders.cancelPendingOrderResponse(id, access.orderOwnerOrDie(guest, id));
    }

    @PostMapping("/guest/payments/create")
    public PaymentCreationResponse pay(
            HttpServletRequest request,
            @RequestBody PaymentCreationRequest payment,
            @RequestHeader("Idempotency-Key") String key) {
        final GuestCheckoutDto guest = guests.validateOrDie(request, true);
        return payments.createPayment(payment, access.orderOwnerOrDie(guest, payment.getOrderId()), key);
    }

    @GetMapping("/guest/payments/{id}/status")
    public PaymentStatusResponse status(HttpServletRequest request, @PathVariable String id) {
        final GuestCheckoutDto guest = guests.validateOrDie(request, false);
        return payments.getPaymentStatus(id, access.paymentOwnerOrDie(guest, id));
    }

    @GetMapping("/guest/payments/{id}/pix-qr-code")
    public PaymentPixQrCodeResponse qr(HttpServletRequest request, @PathVariable String id) {
        final GuestCheckoutDto guest = guests.validateOrDie(request, false);
        return payments.getPixQrCode(id, access.paymentOwnerOrDie(guest, id));
    }
}
