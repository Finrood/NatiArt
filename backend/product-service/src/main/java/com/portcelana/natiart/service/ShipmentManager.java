package com.portcelana.natiart.service;

import java.net.URI;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.OrderDto;
import com.portcelana.natiart.dto.ShipmentDto;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.support.OrderStatus;
import com.portcelana.natiart.repository.OrderRepository;

/** Atomically records a real shipment and its customer-visible tracking information. */
@Service
public class ShipmentManager {
    private final OrderRepository orders;

    public ShipmentManager(OrderRepository orders) {
        this.orders = orders;
    }

    /** Replaying identical carrier details returns the existing milestone without sending another notice. */
    @Transactional
    public OrderDto ship(String orderId, ShipmentDto shipment) {
        if (shipment == null
                || shipment.trackingCode() == null
                || !shipment.trackingCode().trim().matches("[A-Za-z0-9][A-Za-z0-9 ._-]{2,99}")) {
            throw new IllegalArgumentException("A valid carrier tracking code is required");
        }
        final String url = validateUrl(shipment.trackingUrl());
        final CustomerOrder order =
                orders.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        final String code = shipment.trackingCode().trim();
        if ((order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED)
                && code.equals(order.getTrackingCode())
                && java.util.Objects.equals(url, order.getTrackingUrl())) {
            return OrderDto.from(order);
        }
        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new IllegalArgumentException("Only an order in preparation can be shipped");
        }
        order.setShipment(code, url).setStatus(OrderStatus.SHIPPED);
        return OrderDto.from(orders.saveAndFlush(order));
    }

    static String validateUrl(String value) {
        if (value == null || value.isBlank()) return null;
        final String url = value.trim();
        try {
            final URI uri = URI.create(url);
            if (url.length() > 500
                    || !"https".equals(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || url.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Tracking URL must be a public HTTPS carrier URL");
            }
            return url;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Tracking URL must be a public HTTPS carrier URL");
        }
    }
}
