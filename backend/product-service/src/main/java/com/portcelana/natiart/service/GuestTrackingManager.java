package com.portcelana.natiart.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.Payment;
import com.portcelana.natiart.repository.*;

/** The mailbox capability permits reads of original guest order snapshots only. */
@Service
public class GuestTrackingManager {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final OrderViewService views;

    public GuestTrackingManager(OrderRepository orders, PaymentRepository payments, OrderViewService views) {
        this.orders = orders;
        this.payments = payments;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public String ownerOrDie(GuestCheckoutClient.TrackingScope scope, String id) {
        final CustomerOrder order =
                orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order unavailable"));
        if (order.getGuestCustomerId() == null
                || !scope.email().equalsIgnoreCase(order.getEmail())
                || order.getOrderDate().isAfter(scope.cutoff()))
            throw new ResourceNotFoundException("Order unavailable");
        return order.getOwnerExternalId();
    }

    @Transactional(readOnly = true)
    public String paymentOwnerOrDie(GuestCheckoutClient.TrackingScope scope, String id) {
        final Payment payment =
                payments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment unavailable"));
        if (payment.getOrderId() == null) throw new ResourceNotFoundException("Payment unavailable");
        return ownerOrDie(scope, payment.getOrderId());
    }

    @Transactional(readOnly = true)
    public List<OrderDto> history(GuestCheckoutClient.TrackingScope scope, int page) {
        return orders
                .findGuestTrackingIds(scope.email(), scope.cutoff(), PageRequest.of(Math.max(0, page), 20))
                .stream()
                .map(id -> views.getCustomerOrder(id, ownerOrDie(scope, id)))
                .toList();
    }
}
