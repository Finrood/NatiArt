package com.portcelana.natiart.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.UserNotAllowedException;
import com.portcelana.natiart.dto.GuestCheckoutDto;
import com.portcelana.natiart.model.*;
import com.portcelana.natiart.repository.*;

@Service
public class GuestOrderManager {
    private final OrderRepository orders;
    private final PaymentRepository payments;

    public GuestOrderManager(OrderRepository orders, PaymentRepository payments) {
        this.orders = orders;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public String orderOwnerOrDie(GuestCheckoutDto guest, String orderId) {
        final CustomerOrder order =
                orders.findById(orderId).orElseThrow(() -> new UserNotAllowedException("Order unavailable"));
        if (!guest.customerId().equals(order.getGuestCustomerId())
                || !guest.externalId().equals(order.getOwnerExternalId())
                || order.getAccountOwnerId() != null) throw new UserNotAllowedException("Order unavailable");
        return order.getOwnerExternalId();
    }

    @Transactional(readOnly = true)
    public String paymentOwnerOrDie(GuestCheckoutDto guest, String paymentId) {
        final Payment payment =
                payments.findById(paymentId).orElseThrow(() -> new UserNotAllowedException("Payment unavailable"));
        if (payment.getOrderId() == null) throw new UserNotAllowedException("Payment unavailable");
        return orderOwnerOrDie(guest, payment.getOrderId());
    }
}
